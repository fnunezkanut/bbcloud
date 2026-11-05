package com.github.fnunezkanut.bbcloud.git

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import git4idea.commands.GitCommand
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

/**
 * Fetches, inspects and fast-forwards the local source and destination branches of a pull request so they match their
 * `<remote>/<branch>` counterparts on Bitbucket when that is safe.
 *
 * Git runs through the [GitCommandRunner] seam, so the engine holds no platform state and is unit-testable. All
 * functions block and must be called on [kotlinx.coroutines.Dispatchers.IO]. Branch names are always used in full;
 * only a leading `<remote>/` or `refs/heads/` is stripped (see [normalizeBranchName]).
 */
class PrBranchSyncEngine(
    private val gitRunner: GitCommandRunner = PlatformGitCommandRunner(),
    private val openProjectLocator: OpenProjectLocator = OpenProjectLocatorImpl()
) {
    /** Strips only a leading `<remote>/` and a leading `refs/heads/`, never everything before the last slash. */
    fun normalizeBranchName(branchName: String, remoteName: String): String {
        return GitBranchNames.normalize(branchName = branchName, remoteName = remoteName)
    }

    /**
     * Fetches [branches] from [remoteName] into `refs/remotes/<remote>/<branch>` with explicit forced refspecs. When the
     * combined fetch fails and there is more than one branch, each refspec is retried on its own so a missing remote
     * branch does not block the other. Returns true when at least one fetch succeeded.
     */
    fun fetch(project: Project?, prWorktreePath: Path, remoteName: String, remoteUrl: String?, branches: List<String>): Boolean {
        val distinctBranches = branches
            .map { branch -> normalizeBranchName(branchName = branch, remoteName = remoteName) }
            .filter { branch -> branch.isNotBlank() }
            .distinct()
        if (distinctBranches.isEmpty()) {
            return false
        }
        val refspecs = distinctBranches.map { branch -> refspec(remoteName = remoteName, branch = branch) }
        val combined = runFetch(project = project, workingDir = prWorktreePath, remoteName = remoteName, remoteUrl = remoteUrl, refspecs = refspecs)
        if (combined) {
            return true
        }
        if (refspecs.size == 1) {
            return false
        }
        var anySucceeded = false
        for (refspec in refspecs) {
            if (runFetch(project = project, workingDir = prWorktreePath, remoteName = remoteName, remoteUrl = remoteUrl, refspecs = listOf(refspec))) {
                anySucceeded = true
            }
        }
        return anySucceeded
    }

    /** Reads the state of both branches without changing anything. Never throws (other than cancellation). */
    fun inspect(project: Project?, prWorktreePath: Path, remoteName: String, sourceBranch: String, destinationBranch: String): PrBranchSyncSnapshot {
        val source = normalizeBranchName(branchName = sourceBranch, remoteName = remoteName)
        val destination = normalizeBranchName(branchName = destinationBranch, remoteName = remoteName)
        val worktreeList = readWorktreeList(project = project, prWorktreePath = prWorktreePath)
        val sourceStatus = inspectBranch(
            project = project,
            prWorktreePath = prWorktreePath,
            remoteName = remoteName,
            branch = source,
            worktreeList = worktreeList
        )
        val destinationStatus = if (destination == source) {
            sourceStatus
        } else {
            inspectBranch(
                project = project,
                prWorktreePath = prWorktreePath,
                remoteName = remoteName,
                branch = destination,
                worktreeList = worktreeList
            )
        }
        return PrBranchSyncSnapshot(
            remoteName = remoteName,
            sourceBranch = source,
            destinationBranch = destination,
            prWorktreePath = prWorktreePath,
            source = sourceStatus,
            destination = destinationStatus
        )
    }

    /** Fast-forwards the source and then the destination branch according to [PrBranchFastForwardPolicy]. */
    fun fastForward(project: Project?, snapshot: PrBranchSyncSnapshot, remoteUrl: String?, mode: PrBranchSyncMode): PrBranchSyncOutcome {
        val results = linkedMapOf<PrBranchRole, PrBranchSyncStepResult>()
        for (role in PrBranchRole.entries) {
            val isDuplicateDestination = role == PrBranchRole.Destination && snapshot.destinationBranch == snapshot.sourceBranch
            if (isDuplicateDestination) {
                results[role] = PrBranchSyncStepResult.NotNeeded
                continue
            }
            results[role] = fastForwardBranch(project = project, snapshot = snapshot, role = role, remoteUrl = remoteUrl, mode = mode)
        }
        return PrBranchSyncOutcome(results = results)
    }

    /**
     * Runs fetch (when [fetch] is true), inspect, fast-forward and then inspect again. The second inspect runs without a
     * fetch.
     */
    fun sync(
        project: Project?,
        prWorktreePath: Path,
        remoteName: String,
        remoteUrl: String?,
        sourceBranch: String,
        destinationBranch: String,
        mode: PrBranchSyncMode,
        fetch: Boolean = true
    ): PrBranchSyncResult {
        if (fetch) {
            val fetched = fetch(
                project = project,
                prWorktreePath = prWorktreePath,
                remoteName = remoteName,
                remoteUrl = remoteUrl,
                branches = listOf(sourceBranch, destinationBranch)
            )
            if (!fetched) {
                LOG.debug("Fetch of the pull request branches failed; continuing with the existing remote tracking refs")
            }
        }
        val before = inspect(
            project = project,
            prWorktreePath = prWorktreePath,
            remoteName = remoteName,
            sourceBranch = sourceBranch,
            destinationBranch = destinationBranch
        )
        val outcome = fastForward(project = project, snapshot = before, remoteUrl = remoteUrl, mode = mode)
        val after = if (outcome.results.values.any { result -> result !is PrBranchSyncStepResult.NotNeeded && result !is PrBranchSyncStepResult.Skipped }) {
            inspect(
                project = project,
                prWorktreePath = prWorktreePath,
                remoteName = remoteName,
                sourceBranch = sourceBranch,
                destinationBranch = destinationBranch
            )
        } else {
            before
        }
        return PrBranchSyncResult(outcome = outcome, after = after)
    }

    private fun runFetch(project: Project?, workingDir: Path, remoteName: String, remoteUrl: String?, refspecs: List<String>): Boolean {
        return try {
            val result = gitRunner.run(
                project = project,
                workingDir = workingDir,
                command = GitCommand.FETCH,
                parameters = listOf(remoteName) + refspecs,
                remoteUrl = remoteUrl
            )
            if (!result.success) {
                LOG.debug("git fetch $remoteName ${refspecs.size} refspec(s) failed")
            }
            result.success
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.debug("git fetch $remoteName failed unexpectedly", exception)
            false
        }
    }

    private fun readWorktreeList(project: Project?, prWorktreePath: Path): String? {
        val result = runSafely(
            project = project,
            workingDir = prWorktreePath,
            command = GitCommand.WORKTREE,
            parameters = listOf("list", "--porcelain")
        )
        return if (result != null && result.success) result.combinedOutput else null
    }

    private fun inspectBranch(project: Project?, prWorktreePath: Path, remoteName: String, branch: String, worktreeList: String?): BranchSyncStatus {
        if (branch.isBlank()) {
            return BranchSyncStatus.Unknown
        }
        val remoteRef = "refs/remotes/$remoteName/$branch"
        val localRef = "$HEADS_PREFIX$branch"
        val remoteResult = runSafely(
            project = project,
            workingDir = prWorktreePath,
            command = GitCommand.REV_PARSE,
            parameters = listOf("--verify", "--quiet", "$remoteRef^{commit}")
        )
        val remoteRevision = remoteResult?.takeIf { result -> result.success }
            ?.let { result -> GitCommandOutput.lastOutputLine(result.combinedOutput) }
        if (remoteRevision == null) {
            return if (remoteResult == null) BranchSyncStatus.Unknown else BranchSyncStatus.NoRemoteBranch
        }
        val localResult = runSafely(
            project = project,
            workingDir = prWorktreePath,
            command = GitCommand.REV_PARSE,
            parameters = listOf("--verify", "--quiet", "$localRef^{commit}")
        )
        val localRevision = localResult?.takeIf { result -> result.success }
            ?.let { result -> GitCommandOutput.lastOutputLine(result.combinedOutput) }
        if (localRevision == null) {
            return if (localResult == null) BranchSyncStatus.Unknown else BranchSyncStatus.MissingLocal(remoteRevision = remoteRevision)
        }
        val countResult = runSafely(
            project = project,
            workingDir = prWorktreePath,
            command = GitCommand.REV_LIST,
            parameters = listOf("--left-right", "--count", "$localRef...$remoteRef")
        )
        val aheadBehind = countResult?.takeIf { result -> result.success }
            ?.let { result -> GitAheadBehindOutput.parse(result.combinedOutput) }
            ?: return BranchSyncStatus.Unknown
        if (aheadBehind.behind == 0) {
            return BranchSyncStatus.UpToDate(commitsAhead = aheadBehind.ahead)
        }
        if (worktreeList == null) {
            return BranchSyncStatus.Unknown
        }
        val checkedOutAt = WorktreeListOutput.worktreeForBranch(porcelainOutput = worktreeList, branchName = branch)
        val dirty = checkedOutAt?.let { path -> readDirty(project = project, prWorktreePath = prWorktreePath, worktreePath = path) }
        return BranchSyncStatus.Behind(
            commitsBehind = aheadBehind.behind,
            commitsAhead = aheadBehind.ahead,
            localRevision = localRevision,
            remoteRevision = remoteRevision,
            checkedOutAt = checkedOutAt,
            checkedOutWorktreeDirty = dirty
        )
    }

    private fun readDirty(project: Project?, prWorktreePath: Path, worktreePath: Path): Boolean? {
        if (!Files.isDirectory(worktreePath)) {
            return null
        }
        val result = runSafely(
            project = projectFor(project = project, prWorktreePath = prWorktreePath, path = worktreePath),
            workingDir = worktreePath,
            command = GitCommand.STATUS,
            parameters = listOf("--porcelain", "--untracked-files=no")
        )
        if (result == null || !result.success) {
            return null
        }
        return GitStatusOutput.hasUncommittedChanges(GitCommandOutput.withoutCommandLine(result.combinedOutput))
    }

    private fun fastForwardBranch(
        project: Project?,
        snapshot: PrBranchSyncSnapshot,
        role: PrBranchRole,
        remoteUrl: String?,
        mode: PrBranchSyncMode
    ): PrBranchSyncStepResult {
        val branch = snapshot.branchFor(role)
        val status = snapshot.statusFor(role)
        val action = PrBranchFastForwardPolicy.decide(
            role = role,
            status = status,
            prWorktreePath = snapshot.prWorktreePath,
            mode = mode
        )
        val remoteRef = "refs/remotes/${snapshot.remoteName}/$branch"
        return try {
            when (action) {
                PrBranchSyncAction.None -> {
                    PrBranchSyncStepResult.NotNeeded
                }
                is PrBranchSyncAction.Skip -> {
                    LOG.info("Not updating local $role branch '$branch': ${action.reason}")
                    PrBranchSyncStepResult.Skipped(reason = action.reason, path = (status as? BranchSyncStatus.Behind)?.checkedOutAt)
                }
                is PrBranchSyncAction.MergeFastForwardInWorktree -> {
                    val result = runSafely(
                        project = projectFor(project = project, prWorktreePath = snapshot.prWorktreePath, path = action.worktreePath),
                        workingDir = action.worktreePath,
                        command = GitCommand.MERGE,
                        parameters = listOf("--ff-only", remoteRef)
                    )
                    finish(role = role, branch = branch, status = status, result = result, path = action.worktreePath)
                }
                is PrBranchSyncAction.UpdateRef -> {
                    val result = runSafely(
                        project = project,
                        workingDir = snapshot.prWorktreePath,
                        command = GitCommand.UPDATE_REF,
                        parameters = listOf(
                            "-m",
                            "bbcloud: fast-forward $branch to ${snapshot.remoteName}/$branch",
                            "$HEADS_PREFIX$branch",
                            action.newRevision,
                            action.oldRevision
                        )
                    )
                    finish(role = role, branch = branch, status = status, result = result, path = null)
                }
                PrBranchSyncAction.CreateLocalBranch -> {
                    val result = runSafely(
                        project = project,
                        workingDir = snapshot.prWorktreePath,
                        command = GitCommand.BRANCH,
                        parameters = listOf("--track", branch, remoteRef)
                    )
                    if (result != null && result.success) {
                        LOG.info("Created local branch '$branch' from ${snapshot.remoteName}/$branch")
                        PrBranchSyncStepResult.Updated(path = null)
                    } else {
                        failed(result = result)
                    }
                }
                PrBranchSyncAction.PullIntoPrWorktree -> {
                    val result = runSafely(
                        project = project,
                        workingDir = snapshot.prWorktreePath,
                        command = GitCommand.PULL,
                        parameters = listOf("--no-edit", snapshot.remoteName, branch),
                        remoteUrl = remoteUrl
                    )
                    finish(role = role, branch = branch, status = status, result = result, path = snapshot.prWorktreePath)
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.debug("Updating local $role branch '$branch' failed unexpectedly", exception)
            PrBranchSyncStepResult.Failed(output = exception.message.orEmpty())
        }
    }

    private fun finish(role: PrBranchRole, branch: String, status: BranchSyncStatus, result: GitCommandResult?, path: Path?): PrBranchSyncStepResult {
        if (result == null || !result.success) {
            return failed(result = result)
        }
        val commits = (status as? BranchSyncStatus.Behind)?.commitsBehind ?: 0
        LOG.info("Fast-forwarded local branch '$branch' ($role) by $commits commit(s)")
        return PrBranchSyncStepResult.Updated(path = path)
    }

    private fun failed(result: GitCommandResult?): PrBranchSyncStepResult {
        val output = result?.combinedOutput.orEmpty()
        LOG.debug("Updating a local pull request branch failed")
        return PrBranchSyncStepResult.Failed(output = output)
    }

    private fun projectFor(project: Project?, prWorktreePath: Path, path: Path): Project? {
        val normalized = path.toAbsolutePath().normalize()
        if (normalized == prWorktreePath.toAbsolutePath().normalize()) {
            return project
        }
        return openProjectLocator.findOpenProjectForPath(normalized)
    }

    private fun runSafely(project: Project?, workingDir: Path, command: GitCommand, parameters: List<String>, remoteUrl: String? = null): GitCommandResult? {
        return try {
            gitRunner.run(project = project, workingDir = workingDir, command = command, parameters = parameters, remoteUrl = remoteUrl)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.debug("git ${command.name()} failed unexpectedly", exception)
            null
        }
    }

    private fun refspec(remoteName: String, branch: String): String {
        return "+$HEADS_PREFIX$branch:refs/remotes/$remoteName/$branch"
    }

    companion object {
        private val LOG = logger<PrBranchSyncEngine>()
        private const val HEADS_PREFIX = "refs/heads/"
    }
}