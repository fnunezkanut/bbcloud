package com.github.fnunezkanut.bbcloud.git

import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeLayout
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import git4idea.commands.GitCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * Creates and reconciles a git worktree for a pull request, idempotently and without mutating the shared source clone.
 *
 * The worktree is ALWAYS checked out on a local branch named exactly like the pull request source branch (the branch
 * being merged in), based on the freshly fetched source ref, so the worktree stays aligned with the pull request.
 * There is no synthetic fallback branch: when that source-named branch cannot be created or checked out, preparation
 * FAILS and the real git error is returned to the caller, which shows it to the user. This happens, for example, when
 * git refuses to check the same branch out in two worktrees because the source branch is already checked out in the
 * source clone or in another worktree. The source branch is fetched directly from `origin` by its name, which resolves
 * for same-repository pull requests; a fork pull request whose source branch is not present on `origin` fails at the
 * fetch step, and that failure is likewise surfaced to the caller.
 *
 * Git commands run on [Dispatchers.IO]. When a project is already open for the source clone its [GitLineHandler] path
 * is used for consistency with the rest of the plugin; otherwise the commands run through a direct process invocation
 * so worktree creation works even when no source-clone project is open.
 */
class BbCloudWorktreeService {
    // The git runner and project locator are seams injected only in tests via the internal factory below. They are not
    // constructor parameters because the platform instantiates this registered application service and can only
    // resolve a no-arg (or Project/CoroutineScope) constructor; a constructor dependency would fail at runtime (see
    // BbCloudServiceConstructorTest).
    private var gitRunner: GitCommandRunner = PlatformGitCommandRunner()
    private var openProjectLocator: OpenProjectLocator = OpenProjectLocatorImpl()

    /** Serializes git writes per source checkout so concurrent clicks on two pull requests of the same repo do not contend on the index lock. */
    private val sourceLocks = ConcurrentHashMap<Path, Mutex>()

    /**
     * Returns whether [worktreePath] contains uncommitted or untracked changes. An unreadable status is treated as dirty
     * so background pruning fails safe and never silently discards work it cannot inspect.
     */
    suspend fun hasUncommittedChanges(worktreePath: Path): Boolean {
        return withContext(Dispatchers.IO) {
            val normalizedWorktree = worktreePath.toAbsolutePath().normalize()
            readUncommittedChanges(
                project = findOpenProjectForPath(normalizedWorktree),
                worktreePath = normalizedWorktree
            ) != false
        }
    }

    private suspend fun readUncommittedChanges(project: Project?, worktreePath: Path): Boolean? {
        val result = runGit(
            project = project,
            workingDir = worktreePath,
            command = GitCommand.STATUS,
            parameters = listOf("--porcelain", "--untracked-files=normal")
        )
        return if (result.success) GitStatusOutput.hasUncommittedChanges(result.combinedOutput) else null
    }

    suspend fun removeWorktree(sourceCheckoutRoot: Path, worktreePath: Path, workTreeDirectory: Path): WorktreeRemovalResult {
        val normalizedSource = sourceCheckoutRoot.toAbsolutePath().normalize()
        val normalizedWorktree = worktreePath.toAbsolutePath().normalize()
        val normalizedWorkTreeDirectory = workTreeDirectory.toAbsolutePath().normalize()
        val targetName = normalizedWorktree.fileName?.toString()
        val isSafeTarget = normalizedWorktree.parent == normalizedWorkTreeDirectory &&
            targetName != null &&
            PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(targetName) != null
        if (!isSafeTarget) {
            return WorktreeRemovalResult.Failed(output = "refused to remove an unexpected worktree path: $normalizedWorktree")
        }

        val lock = sourceLocks.getOrPut(normalizedSource) { Mutex() }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                val sourceProject = findOpenProjectForPath(normalizedSource)
                if (Files.exists(normalizedWorktree)) {
                    val removeResult = runGit(
                        project = sourceProject,
                        workingDir = normalizedSource,
                        command = GitCommand.WORKTREE,
                        parameters = listOf("remove", "--force", normalizedWorktree.toString())
                    )
                    if (!removeResult.success) {
                        return@withContext WorktreeRemovalResult.Failed(output = removeResult.combinedOutput)
                    }
                }

                val pruneResult = runGit(
                    project = sourceProject,
                    workingDir = normalizedSource,
                    command = GitCommand.WORKTREE,
                    parameters = listOf("prune")
                )
                refreshWorktreeInVfs(worktreePath = normalizedWorktree)
                if (!pruneResult.success) {
                    WorktreeRemovalResult.Failed(output = pruneResult.combinedOutput)
                } else {
                    LOG.debug("Removed or reconciled declined pull request worktree at $normalizedWorktree")
                    WorktreeRemovalResult.Success
                }
            }
        }
    }

    suspend fun ensureWorktree(sourceCheckoutRoot: Path, worktreePath: Path, remoteUrl: String?, prNumber: Long, sourceBranch: String): WorktreeSetupResult {
        val normalizedSource = sourceCheckoutRoot.toAbsolutePath().normalize()
        val normalizedWorktree = worktreePath.toAbsolutePath().normalize()
        val lock = sourceLocks.getOrPut(normalizedSource) { Mutex() }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                prepareWorktree(
                    sourceCheckoutRoot = normalizedSource,
                    worktreePath = normalizedWorktree,
                    remoteUrl = remoteUrl,
                    prNumber = prNumber,
                    sourceBranch = sourceBranch
                )
            }
        }
    }

    private suspend fun prepareWorktree(
        sourceCheckoutRoot: Path,
        worktreePath: Path,
        remoteUrl: String?,
        prNumber: Long,
        sourceBranch: String
    ): WorktreeSetupResult {
        val sourceProject = findOpenProjectForPath(sourceCheckoutRoot)
        // The worktree is always put on a local branch named exactly like the pull request source branch (the branch
        // being merged in). There is no synthetic fallback: if this branch cannot be created or checked out, the real
        // git error is surfaced to the caller (see the class KDoc).
        val branchName = sourceBranch.trim()

        // Defence in depth: the parser drops pull requests whose source branch name is blank, so this should not
        // happen via the normal path, but a blank branch name cannot be used with git and must fail clearly.
        if (branchName.isBlank()) {
            LOG.info("Cannot prepare worktree for #$prNumber: the pull request source branch name is blank")
            return WorktreeSetupResult.Failed(output = "pull request source branch name is blank")
        }

        // G3: the source must be a usable git repository with an origin remote before any worktree work.
        if (remoteUrl == null) {
            LOG.debug("Source checkout $sourceCheckoutRoot has no origin remote URL; cannot prepare worktree for #$prNumber")
            return WorktreeSetupResult.Failed(output = "source checkout has no origin remote: $sourceCheckoutRoot")
        }

        // Requirement #2: create the work tree root (the parent of the flattened $repoSlug--pr-$prNumber folder, i.e.
        // $projectsDirectory/.worktrees) when missing, since git worktree add does not create missing parents.
        val parentFolder = worktreePath.parent
        try {
            if (parentFolder != null && !Files.isDirectory(parentFolder)) {
                Files.createDirectories(parentFolder)
            }
        } catch (exception: IOException) {
            LOG.debug("Could not create work tree parent folder $parentFolder", exception)
            return WorktreeSetupResult.Failed(output = "could not create work tree folder $parentFolder: ${exception.message}", cause = exception)
        }

        coroutineContext.ensureActive()

        // Fetch the pull request source branch so the worktree can be based on FETCH_HEAD. The Bitbucket Cloud
        // refs/pull-requests/<id>/from ref is not reliably exposed on every remote, so the branch name being merged
        // is used directly.
        val fetchResult = runGit(
            project = sourceProject,
            workingDir = sourceCheckoutRoot,
            command = GitCommand.FETCH,
            remoteUrl = remoteUrl,
            parameters = listOf("origin", branchName)
        )
        if (!fetchResult.success) {
            return WorktreeSetupResult.Failed(output = fetchResult.combinedOutput)
        }

        coroutineContext.ensureActive()

        // D2: clear registrations whose directories are gone before reconciling the target path.
        runGit(project = sourceProject, workingDir = sourceCheckoutRoot, command = GitCommand.WORKTREE, parameters = listOf("prune"))

        val reconcileOutcome: ReconcileOutcome.Prepared
        try {
            reconcileOutcome = reconcileWorktree(
                sourceProject = sourceProject,
                sourceCheckoutRoot = sourceCheckoutRoot,
                worktreePath = worktreePath,
                branchName = branchName,
                prNumber = prNumber
            ).let { result ->
                when (result) {
                    is ReconcileOutcome.Failed -> {
                        return WorktreeSetupResult.Failed(output = result.output)
                    }
                    is ReconcileOutcome.BranchCheckedOutInSource -> {
                        LOG.info(
                            "Cannot open pull request #$prNumber in its own work tree: the source clone at " +
                                "$sourceCheckoutRoot is on the pull request branch '${result.branchName}'"
                        )
                        return WorktreeSetupResult.BranchCheckedOutInSource(
                            branchName = result.branchName,
                            sourceCheckoutPath = sourceCheckoutRoot
                        )
                    }
                    is ReconcileOutcome.Prepared -> {
                        result
                    }
                }
            }
        } catch (exception: CancellationException) {
            // D5: a cancellation during add leaves a half-made worktree; clean up only what this invocation created.
            cleanupPartialWorktree(sourceProject = sourceProject, sourceCheckoutRoot = sourceCheckoutRoot, worktreePath = worktreePath)
            throw exception
        }

        refreshWorktreeInVfs(worktreePath = worktreePath)
        LOG.debug(
            "Prepared worktree at $worktreePath on branch ${reconcileOutcome.branchName} " +
                "(createdByUs=${reconcileOutcome.createdByUs})"
        )
        return WorktreeSetupResult.Success(worktreePath = worktreePath)
    }

    private sealed interface ReconcileOutcome {
        data class Prepared(val createdByUs: Boolean, val branchName: String) : ReconcileOutcome

        data class Failed(val output: String) : ReconcileOutcome

        /**
         * The worktree could not be prepared because the source clone is currently on the pull request branch, so git
         * refuses to check it out a second time. Carried up so the UI shows an explanatory modal, not the raw balloon.
         */
        data class BranchCheckedOutInSource(val branchName: String) : ReconcileOutcome
    }

    /**
     * Brings the target [worktreePath] into existence on [branchName] (the pull request source branch), reusing an
     * already-registered worktree when present and forcing past a stale directory that git no longer tracks. There is
     * no fallback branch: when git refuses the source branch (for example because it is already checked out elsewhere),
     * the real git output is returned in [ReconcileOutcome.Failed]. The returned [ReconcileOutcome.Prepared] reports
     * the branch in effect and whether this call created the worktree, so a later cancellation only removes worktrees
     * this invocation made.
     */
    private suspend fun reconcileWorktree(
        sourceProject: Project?,
        sourceCheckoutRoot: Path,
        worktreePath: Path,
        branchName: String,
        prNumber: Long
    ): ReconcileOutcome {
        val listResult = runGit(
            project = sourceProject,
            workingDir = sourceCheckoutRoot,
            command = GitCommand.WORKTREE,
            parameters = listOf("list", "--porcelain")
        )
        if (!listResult.success) {
            return ReconcileOutcome.Failed(output = listResult.combinedOutput)
        }
        val alreadyRegistered = WorktreeListOutput.containsWorktree(porcelainOutput = listResult.combinedOutput, worktreePath = worktreePath)

        if (alreadyRegistered && Files.isDirectory(worktreePath)) {
            // Reuse the existing worktree, keeping any uncommitted work the user has in it, and put a clean worktree on
            // the source branch.
            return reuseExistingWorktree(
                sourceProject = sourceProject,
                sourceCheckoutRoot = sourceCheckoutRoot,
                worktreePath = worktreePath,
                branchName = branchName
            )
        }

        coroutineContext.ensureActive()

        return addFreshWorktree(
            sourceProject = sourceProject,
            sourceCheckoutRoot = sourceCheckoutRoot,
            worktreePath = worktreePath,
            branchName = branchName,
            prNumber = prNumber
        )
    }

    /**
     * Reuses an already-registered worktree. A dirty worktree is left on its current branch so the user keeps any
     * in-progress work (D-5); a clean one is moved onto [branchName]. Returns a [ReconcileOutcome] reporting the branch
     * in effect, or a failure carrying the git output when git refuses the checkout.
     */
    private suspend fun reuseExistingWorktree(sourceProject: Project?, sourceCheckoutRoot: Path, worktreePath: Path, branchName: String): ReconcileOutcome {
        val ensured = ensureExistingWorktreeOnBranch(
            sourceProject = sourceProject,
            worktreePath = worktreePath,
            branchName = branchName
        )
        return if (ensured == null) {
            ReconcileOutcome.Prepared(createdByUs = false, branchName = branchName)
        } else {
            // A reused worktree is already registered on this branch, so the branch exists; when the checkout failed
            // because the source clone is sitting on the same branch, surface it as the collision instead of the raw
            // git output. Any other checkout failure falls through to the generic failure.
            classifyBranchOperationFailure(
                sourceProject = sourceProject,
                sourceCheckoutRoot = sourceCheckoutRoot,
                branchName = branchName,
                branchExists = true,
                failureOutput = ensured
            )
        }
    }

    /**
     * Creates the worktree directory with `git worktree add` on [branchName] (the pull request source branch). When a
     * local branch of that name already exists it is checked out; otherwise it is created fresh at the freshly fetched
     * ref (`FETCH_HEAD`). There is no fallback: when git rejects the branch (for example because it is already checked
     * out elsewhere), the git output is returned in [ReconcileOutcome.Failed] for the caller to show to the user.
     */
    private suspend fun addFreshWorktree(
        sourceProject: Project?,
        sourceCheckoutRoot: Path,
        worktreePath: Path,
        branchName: String,
        prNumber: Long
    ): ReconcileOutcome {
        coroutineContext.ensureActive()

        val branchExists = localBranchExists(sourceProject = sourceProject, sourceCheckoutRoot = sourceCheckoutRoot, branchName = branchName)

        // A directory left by a previous (possibly failed) attempt is not tracked by git; force past it.
        val directoryExists = Files.exists(worktreePath)
        val addParameters = buildList {
            add("add")
            if (directoryExists) {
                add("--force")
            }
            if (branchExists) {
                // The branch already exists, so check it out rather than recreating it.
                add(worktreePath.toString())
                add(branchName)
            } else {
                // Create the branch pointing at the freshly fetched ref.
                add("-b")
                add(branchName)
                add(worktreePath.toString())
                add("FETCH_HEAD")
            }
        }
        val addResult = runGit(
            project = sourceProject,
            workingDir = sourceCheckoutRoot,
            command = GitCommand.WORKTREE,
            parameters = addParameters
        )
        if (addResult.success) {
            return ReconcileOutcome.Prepared(createdByUs = true, branchName = branchName)
        }
        LOG.debug("git worktree add on branch $branchName failed for pull request #$prNumber")
        return classifyBranchOperationFailure(
            sourceProject = sourceProject,
            sourceCheckoutRoot = sourceCheckoutRoot,
            branchName = branchName,
            branchExists = branchExists,
            failureOutput = addResult.combinedOutput
        )
    }

    /**
     * Turns a failed worktree branch operation into a [ReconcileOutcome]. When the source clone is currently on
     * [branchName] (and the branch is a real local branch, not one being freshly created), git refused the checkout
     * because the same branch cannot be checked out twice; that is the user-resolvable collision, mapped to
     * [ReconcileOutcome.BranchCheckedOutInSource]. Every other failure falls through to the generic
     * [ReconcileOutcome.Failed] carrying the raw git output, so unrelated failures are never reclassified.
     */
    private suspend fun classifyBranchOperationFailure(
        sourceProject: Project?,
        sourceCheckoutRoot: Path,
        branchName: String,
        branchExists: Boolean,
        failureOutput: String
    ): ReconcileOutcome {
        val currentBranch = currentBranchOfSource(sourceProject = sourceProject, sourceCheckoutRoot = sourceCheckoutRoot)
        return if (WorktreeBranchCollision.isCollision(currentBranch = currentBranch, prBranch = branchName, branchExists = branchExists)) {
            ReconcileOutcome.BranchCheckedOutInSource(branchName = branchName)
        } else {
            ReconcileOutcome.Failed(output = failureOutput)
        }
    }

    /**
     * Ensures an already-existing worktree is on [branchName]. When the worktree has uncommitted changes the branch is
     * left as-is so the user keeps their in-progress work (D3.1); otherwise it is checked out onto [branchName].
     * Returns null on success, or the failing git output.
     */
    private suspend fun ensureExistingWorktreeOnBranch(sourceProject: Project?, worktreePath: Path, branchName: String): String? {
        val currentBranchResult = runGit(
            project = sourceProject,
            workingDir = worktreePath,
            command = GitCommand.REV_PARSE,
            parameters = listOf("--abbrev-ref", "HEAD")
        )
        val currentBranch = if (currentBranchResult.success) currentBranchResult.combinedOutput.trim() else null
        if (currentBranch == branchName) {
            return null
        }

        val hasUncommittedChanges = readUncommittedChanges(
            project = sourceProject,
            worktreePath = worktreePath
        ) == true
        if (hasUncommittedChanges) {
            LOG.info(
                "Reusing worktree $worktreePath as-is: it has uncommitted changes on ${currentBranch ?: "an unknown branch"}, not switching to $branchName"
            )
            return null
        }

        coroutineContext.ensureActive()
        val checkoutResult = runGit(
            project = sourceProject,
            workingDir = worktreePath,
            command = GitCommand.CHECKOUT,
            parameters = listOf(branchName)
        )
        return if (checkoutResult.success) null else checkoutResult.combinedOutput
    }

    /**
     * Reads the source clone's current branch via `git rev-parse --abbrev-ref HEAD`, mirroring
     * [BbCloudBranchCheckoutService.runRevParseProbe]. Returns null when the command fails, when HEAD is detached
     * (`"HEAD"`), or when there is no branch, so the collision check can never match on a non-branch state.
     */
    private suspend fun currentBranchOfSource(sourceProject: Project?, sourceCheckoutRoot: Path): String? {
        val result = runGit(
            project = sourceProject,
            workingDir = sourceCheckoutRoot,
            command = GitCommand.REV_PARSE,
            parameters = listOf("--abbrev-ref", "HEAD")
        )
        if (!result.success) {
            return null
        }
        // combinedOutput is formatted as "$ <command line>\n<stdout>\n<stderr>"; the real branch is the last non-blank
        // line, not the "$ ..." command-line prefix. Extracting the last non-blank line is robust whether or not the
        // runner includes the prefix.
        val branch = result.combinedOutput
            .lineSequence()
            .map { line -> line.trim() }
            .lastOrNull { line -> line.isNotBlank() && !line.startsWith("$ ") }
            ?: return null
        return if (branch == "HEAD") null else branch
    }

    private suspend fun localBranchExists(sourceProject: Project?, sourceCheckoutRoot: Path, branchName: String): Boolean {
        val result = runGit(
            project = sourceProject,
            workingDir = sourceCheckoutRoot,
            command = GitCommand.REV_PARSE,
            parameters = listOf("--verify", "--quiet", "refs/heads/$branchName")
        )
        return result.success
    }

    private fun cleanupPartialWorktree(sourceProject: Project?, sourceCheckoutRoot: Path, worktreePath: Path) {
        try {
            runGitBlocking(
                project = sourceProject,
                workingDir = sourceCheckoutRoot,
                command = GitCommand.WORKTREE,
                parameters = listOf("remove", "--force", worktreePath.toString())
            )
            runGitBlocking(project = sourceProject, workingDir = sourceCheckoutRoot, command = GitCommand.WORKTREE, parameters = listOf("prune"))
        } catch (exception: Exception) {
            LOG.debug("Could not clean up the partially created worktree at $worktreePath", exception)
        }
    }

    private fun refreshWorktreeInVfs(worktreePath: Path) {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(worktreePath)
        if (virtualFile != null) {
            VfsUtil.markDirtyAndRefresh(false, true, false, virtualFile)
        }
    }

    private fun findOpenProjectForPath(path: Path): Project? {
        return openProjectLocator.findOpenProjectForPath(path)
    }

    private suspend fun runGit(
        project: Project?,
        workingDir: Path,
        command: GitCommand,
        parameters: List<String>,
        remoteUrl: String? = null
    ): GitCommandResult {
        coroutineContext.ensureActive()
        return runGitBlocking(project = project, workingDir = workingDir, command = command, parameters = parameters, remoteUrl = remoteUrl)
    }

    private fun runGitBlocking(
        project: Project?,
        workingDir: Path,
        command: GitCommand,
        parameters: List<String>,
        remoteUrl: String? = null
    ): GitCommandResult {
        return gitRunner.run(project = project, workingDir = workingDir, command = command, parameters = parameters, remoteUrl = remoteUrl)
    }

    companion object {
        private val LOG = logger<BbCloudWorktreeService>()

        fun getInstance(): BbCloudWorktreeService {
            return ApplicationManager.getApplication().service()
        }

        /**
         * Builds an instance with the git and project-lookup seams stubbed, for unit tests only. Production code must
         * use [getInstance] so the platform owns the single application-service instance and the real implementations
         * are used.
         */
        internal fun forTesting(gitRunner: GitCommandRunner, openProjectLocator: OpenProjectLocator): BbCloudWorktreeService {
            return BbCloudWorktreeService().apply {
                this.gitRunner = gitRunner
                this.openProjectLocator = openProjectLocator
            }
        }
    }
}