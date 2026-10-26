package com.github.fnunezkanut.bbcloud.git

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.changes.GitChangeUtils
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerInputProcessorUtil
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class BbCloudPullRequestBranchDiffService {
    fun loadPullRequestChanges(project: Project, destinationBranch: String, sourceBranch: String): BranchDiffResult {
        val repository = awaitRepository(project) ?: run {
            LOG.info("Cannot load pull request file changes: no Git repository for project ${project.name}")
            return BranchDiffResult.NoRepository
        }

        val destinationRef = resolveBranchRef(repository = repository, branchName = destinationBranch)
        if (destinationRef == null) {
            fetchBranch(project = project, repository = repository, branchName = destinationBranch)
            repository.update()
        }
        val resolvedDestination = resolveBranchRef(repository = repository, branchName = destinationBranch)
        if (resolvedDestination == null) {
            LOG.info("Cannot resolve destination branch '$destinationBranch' in ${repository.root.path}")
            return BranchDiffResult.BranchNotFound(branchName = destinationBranch)
        }

        val sourceRef = resolveBranchRef(repository = repository, branchName = sourceBranch)
        if (sourceRef == null) {
            fetchBranch(project = project, repository = repository, branchName = sourceBranch)
            repository.update()
        }
        val resolvedSource = resolveBranchRef(repository = repository, branchName = sourceBranch)
        if (resolvedSource == null) {
            LOG.info("Cannot resolve source branch '$sourceBranch' in ${repository.root.path}")
            return BranchDiffResult.BranchNotFound(branchName = sourceBranch)
        }

        return try {
            val changes = GitChangeUtils.getThreeDotDiffOrThrow(
                repository,
                resolvedDestination,
                resolvedSource
            )
            LOG.debug(
                "Loaded ${changes.size} pull request file changes between $resolvedDestination and $resolvedSource " +
                    "in ${repository.root.path}"
            )
            BranchDiffResult.Success(
                changes = changes,
                destinationRef = resolvedDestination,
                sourceRef = resolvedSource
            )
        } catch (exception: VcsException) {
            LOG.debug("Git three-dot diff failed between $resolvedDestination and $resolvedSource", exception)
            BranchDiffResult.Failed(message = exception.message.orEmpty(), cause = exception)
        }
    }

    /**
     * Resolves source-branch blob ids for the files in [changes].
     *
     * Failures return an empty map so callers can still use the diff without revision-based features (for example
     * viewed-file tracking).
     */
    fun loadPullRequestFileRevisions(project: Project, sourceRef: String, changes: Collection<Change>): Map<String, String> {
        val repository = awaitRepository(project) ?: run {
            LOG.debug("Skipping file revision lookup: no Git repository for project ${project.name}")
            return emptyMap()
        }
        return loadFileRevisions(
            project = project,
            repository = repository,
            sourceRef = sourceRef,
            changes = changes
        )
    }

    private fun loadFileRevisions(project: Project, repository: GitRepository, sourceRef: String, changes: Collection<Change>): Map<String, String> {
        val repositoryRelativePaths = changes
            .map { change ->
                PullRequestPathMatcher.relativeProjectPath(
                    projectBasePath = repository.root.path,
                    absoluteOrRelativePath = ChangesUtil.getFilePath(change).path
                )
            }
            .map { path -> PullRequestPathMatcher.normalize(path) }
            .distinct()
        if (repositoryRelativePaths.isEmpty()) {
            return emptyMap()
        }
        return try {
            val requests = PullRequestFileRevisionParser.requestLines(
                sourceRef = sourceRef,
                repositoryRelativePaths = repositoryRelativePaths
            )
            val handler = GitLineHandler(project, repository.root, GitCommand.CAT_FILE).apply {
                addParameters("--batch-check")
                // git cat-file --batch-check reads until EOF, so stdin must be closed after the requests are written.
                setInputProcessor(
                    GitHandlerInputProcessorUtil.writeLines(
                        requests,
                        "\n",
                        StandardCharsets.UTF_8,
                        true
                    )
                )
            }
            val commandLine = handler.printableCommandLine()
            LOG.debug("Running $commandLine")
            val started = TimeSource.Monotonic.markNow()
            val result = Git.getInstance().runCommand(handler)
            LOG.debug(
                "Finished $commandLine: exitCode=${result.exitCode} in " +
                    "${started.elapsedNow().inWholeMilliseconds} ms"
            )
            if (!result.success()) {
                LOG.warn("$commandLine failed: exitCode=${result.exitCode}")
                return emptyMap()
            }
            val revisions = PullRequestFileRevisionParser.parse(requestLines = requests, output = result.output)
            LOG.debug("Loaded ${revisions.size} file revision(s) at $sourceRef")
            revisions
        } catch (exception: Exception) {
            LOG.debug("Git cat-file revision lookup failed at $sourceRef", exception)
            emptyMap()
        }
    }

    fun loadPullRequestLineTotals(project: Project, destinationBranch: String, sourceBranch: String): BranchDiffLineTotals? {
        val repository = awaitRepository(project) ?: run {
            LOG.info("Cannot load pull request line totals: no Git repository for project ${project.name}")
            return null
        }

        val destinationRef = resolveBranchRef(repository = repository, branchName = destinationBranch)
        if (destinationRef == null) {
            fetchBranch(project = project, repository = repository, branchName = destinationBranch)
            repository.update()
        }
        val resolvedDestination = resolveBranchRef(repository = repository, branchName = destinationBranch)
        if (resolvedDestination == null) {
            LOG.info("Cannot resolve destination branch '$destinationBranch' in ${repository.root.path}")
            return null
        }

        val sourceRef = resolveBranchRef(repository = repository, branchName = sourceBranch)
        if (sourceRef == null) {
            fetchBranch(project = project, repository = repository, branchName = sourceBranch)
            repository.update()
        }
        val resolvedSource = resolveBranchRef(repository = repository, branchName = sourceBranch)
        if (resolvedSource == null) {
            LOG.info("Cannot resolve source branch '$sourceBranch' in ${repository.root.path}")
            return null
        }

        return try {
            val handler = GitLineHandler(project, repository.root, GitCommand.DIFF).apply {
                addParameters("--numstat", "$resolvedDestination...$resolvedSource")
            }
            val commandLine = handler.printableCommandLine()
            LOG.debug("Running $commandLine")
            val started = TimeSource.Monotonic.markNow()
            val result = Git.getInstance().runCommand(handler)
            LOG.debug(
                "Finished $commandLine: exitCode=${result.exitCode} in " +
                    "${started.elapsedNow().inWholeMilliseconds} ms"
            )
            if (!result.success()) {
                LOG.debug(
                    "git diff --numstat failed between $resolvedDestination and $resolvedSource: " +
                        "exitCode=${result.exitCode}"
                )
                return null
            }
            val totals = GitNumstatParser.sum(result.output)
            LOG.debug(
                "Loaded pull request line totals between $resolvedDestination and $resolvedSource: " +
                    "added=${totals.linesAdded}, removed=${totals.linesRemoved}, " +
                    "hasBinaryFiles=${totals.hasBinaryFiles}"
            )
            totals
        } catch (exception: Exception) {
            LOG.debug("Git three-dot numstat failed between $resolvedDestination and $resolvedSource", exception)
            null
        }
    }

    /**
     * Loads added and removed line counts for each file in the pull request's local three-dot diff.
     *
     * Returns an empty map when the repository, branches, or diff cannot be resolved. This function blocks while the
     * repository and Git command are resolved and must be called on [kotlinx.coroutines.Dispatchers.IO].
     */
    fun loadPullRequestFileLineCounts(project: Project, destinationBranch: String, sourceBranch: String): Map<String, FileLineCount> {
        val repository = awaitRepository(project) ?: run {
            LOG.info("Cannot load pull request file line counts: no Git repository for project ${project.name}")
            return emptyMap()
        }

        val destinationRef = resolveBranchRef(repository = repository, branchName = destinationBranch)
        if (destinationRef == null) {
            fetchBranch(project = project, repository = repository, branchName = destinationBranch)
            repository.update()
        }
        val resolvedDestination = resolveBranchRef(repository = repository, branchName = destinationBranch)
        if (resolvedDestination == null) {
            LOG.info("Cannot resolve destination branch '$destinationBranch' in ${repository.root.path}")
            return emptyMap()
        }

        val sourceRef = resolveBranchRef(repository = repository, branchName = sourceBranch)
        if (sourceRef == null) {
            fetchBranch(project = project, repository = repository, branchName = sourceBranch)
            repository.update()
        }
        val resolvedSource = resolveBranchRef(repository = repository, branchName = sourceBranch)
        if (resolvedSource == null) {
            LOG.info("Cannot resolve source branch '$sourceBranch' in ${repository.root.path}")
            return emptyMap()
        }

        return try {
            val handler = GitLineHandler(project, repository.root, GitCommand.DIFF).apply {
                addParameters("--numstat", "$resolvedDestination...$resolvedSource")
            }
            val commandLine = handler.printableCommandLine()
            LOG.debug("Running $commandLine")
            val started = TimeSource.Monotonic.markNow()
            val result = Git.getInstance().runCommand(handler)
            LOG.debug(
                "Finished $commandLine: exitCode=${result.exitCode} in " +
                    "${started.elapsedNow().inWholeMilliseconds} ms"
            )
            if (!result.success()) {
                LOG.debug(
                    "git diff --numstat failed while loading per-file counts between " +
                        "$resolvedDestination and $resolvedSource: exitCode=${result.exitCode}"
                )
                return emptyMap()
            }
            val counts = GitNumstatParser.perFile(result.output)
            LOG.debug(
                "Loaded per-file line counts between $resolvedDestination and $resolvedSource: " +
                    "${counts.size} file(s)"
            )
            counts
        } catch (exception: Exception) {
            LOG.debug("Git three-dot per-file numstat failed between $resolvedDestination and $resolvedSource", exception)
            emptyMap()
        }
    }

    /**
     * Reads the HEAD of the project's Git work tree.
     *
     * Returns the current branch name (null when the HEAD is detached) and the current revision, or null when the
     * project has no Git repository. The repository lookup tolerates the short window after a work tree project is
     * first opened during which [GitRepositoryManager] has not yet registered the repository.
     *
     * This function blocks while the repository is resolved and must be called on [kotlinx.coroutines.Dispatchers.IO].
     */
    fun readHead(project: Project): WorktreeHead? {
        val repository = awaitRepository(project) ?: run {
            LOG.info("Cannot read work tree HEAD: no Git repository for project ${project.name}")
            return null
        }
        val head = WorktreeHead(
            branchName = repository.currentBranchName,
            revision = repository.currentRevision
        )
        LOG.debug("Read work tree HEAD for project ${project.name}: branch=${head.branchName}, revision=${head.revision}")
        return head
    }

    /**
     * Checks whether [descendantRevision] is a descendant of [ancestorRevision] (or the same commit) by running
     * `git merge-base --is-ancestor <ancestorRevision> <descendantRevision>`.
     *
     * Returns true only when the git command exits with code 0. A non-zero exit, a blank revision, a missing Git
     * repository, or a failing command all yield false.
     *
     * This function blocks while the git command runs and must be called on [kotlinx.coroutines.Dispatchers.IO].
     */
    fun isAncestor(project: Project, ancestorRevision: String, descendantRevision: String): Boolean {
        if (ancestorRevision.isBlank() || descendantRevision.isBlank()) {
            LOG.debug("Cannot check revision ancestry: revision is blank")
            return false
        }
        val repository = awaitRepository(project) ?: run {
            LOG.info("Cannot check revision ancestry: no Git repository for project ${project.name}")
            return false
        }
        return try {
            val handler = GitLineHandler(project, repository.root, GitCommand.MERGE_BASE).apply {
                addParameters("--is-ancestor", ancestorRevision, descendantRevision)
            }
            val commandLine = handler.printableCommandLine()
            LOG.debug("Running $commandLine")
            val started = TimeSource.Monotonic.markNow()
            val result = Git.getInstance().runCommand(handler)
            LOG.debug(
                "Finished $commandLine: exitCode=${result.exitCode} in " +
                    "${started.elapsedNow().inWholeMilliseconds} ms"
            )
            if (result.success()) {
                true
            } else {
                LOG.debug(
                    "$ancestorRevision is not an ancestor of $descendantRevision: exitCode=${result.exitCode}"
                )
                false
            }
        } catch (exception: Exception) {
            LOG.debug("Git merge-base --is-ancestor failed for $ancestorRevision and $descendantRevision", exception)
            false
        }
    }

    /**
     * Evaluates whether the local pull-request branch is behind `origin/<sourceBranch>`.
     *
     * Fetches the source branch (so the remote ref is current), gates on the work tree being checked out on the pull
     * request source branch (decision 3b), then counts the commits `origin/<sourceBranch>` carries that the local HEAD
     * does not (decision 4a). Any failure along the way — no repository, no remote ref, a failing git command — yields
     * [LocalBehindRemoteDecision.Hidden] so no stale "behind" state is shown (decision 5a).
     *
     * This function blocks while the repository, the fetch, and the git commands run and must be called on
     * [kotlinx.coroutines.Dispatchers.IO].
     */
    fun evaluateLocalBehindRemote(project: Project, sourceBranch: String): LocalBehindRemoteDecision {
        val repository = awaitRepository(project) ?: run {
            LOG.info("Cannot evaluate local-behind-remote: no Git repository for project ${project.name}")
            return LocalBehindRemoteDecision.Hidden
        }
        val currentBranchName = repository.currentBranchName
        val localRevision = repository.currentRevision

        // Fetch the source branch so origin/<sourceBranch> reflects the remote before counting (decision 8b triggers
        // call this repeatedly; the fetch keeps the comparison honest).
        fetchBranch(project = project, repository = repository, branchName = sourceBranch)
        repository.update()

        val remoteRef = resolveRemoteSourceRef(repository = repository, sourceBranch = sourceBranch)
        if (remoteRef == null) {
            LOG.debug("Cannot evaluate local-behind-remote: no remote ref for source branch '$sourceBranch' in ${repository.root.path}")
            return LocalBehindRemoteDecision.Hidden
        }
        val remoteRevision = resolveRevision(project = project, refName = remoteRef)
        if (remoteRevision.isNullOrBlank()) {
            LOG.debug("Cannot evaluate local-behind-remote: '$remoteRef' did not resolve to a revision in ${repository.root.path}")
            return LocalBehindRemoteDecision.Hidden
        }
        if (localRevision.isNullOrBlank()) {
            LOG.debug("Cannot evaluate local-behind-remote: the work tree HEAD has no revision in ${repository.root.path}")
            return LocalBehindRemoteDecision.Hidden
        }
        val commitsBehindRemote = countCommitsBetween(
            project = project,
            fromRevision = localRevision,
            toRevision = remoteRevision
        )
        val decision = LocalPrBranchBehindRemoteCheck.evaluate(
            currentBranchName = currentBranchName,
            sourceBranch = sourceBranch,
            localRevision = localRevision,
            commitsBehindRemote = commitsBehindRemote
        )
        LOG.debug(
            "Evaluated local-behind-remote for '$sourceBranch' in ${repository.root.path}: " +
                "branch=$currentBranchName, behind=$commitsBehindRemote, decision=$decision"
        )
        return decision
    }

    /**
     * Resolves a single revision for [refName] by running `git rev-parse --verify <refName>`.
     *
     * Returns the resolved revision, or null when the ref does not exist, is blank, the project has no Git repository,
     * or the git command fails.
     *
     * This function blocks while the git command runs and must be called on [kotlinx.coroutines.Dispatchers.IO].
     */
    fun resolveRevision(project: Project, refName: String): String? {
        if (refName.isBlank()) {
            LOG.debug("Cannot resolve revision: ref name is blank")
            return null
        }
        val repository = awaitRepository(project) ?: run {
            LOG.info("Cannot resolve revision: no Git repository for project ${project.name}")
            return null
        }
        return try {
            val handler = GitLineHandler(project, repository.root, GitCommand.REV_PARSE).apply {
                addParameters("--verify", "--quiet", refName)
            }
            val commandLine = handler.printableCommandLine()
            LOG.debug("Running $commandLine")
            val started = TimeSource.Monotonic.markNow()
            val result = Git.getInstance().runCommand(handler)
            LOG.debug(
                "Finished $commandLine: exitCode=${result.exitCode} in " +
                    "${started.elapsedNow().inWholeMilliseconds} ms"
            )
            if (!result.success()) {
                LOG.debug("rev-parse of '$refName' failed: exitCode=${result.exitCode}")
                return null
            }
            result.outputAsJoinedString.trim().takeIf { revision -> revision.isNotBlank() }
        } catch (exception: Exception) {
            LOG.debug("Git rev-parse failed for '$refName'", exception)
            null
        }
    }

    /**
     * Counts the commits reachable from [toRevision] that are not reachable from [fromRevision] by running
     * `git rev-list --count <fromRevision>..<toRevision>`.
     *
     * Returns the count, or null when either revision is blank, the project has no Git repository, the git command
     * fails, or the output is not a number.
     *
     * This function blocks while the git command runs and must be called on [kotlinx.coroutines.Dispatchers.IO].
     */
    fun countCommitsBetween(project: Project, fromRevision: String, toRevision: String): Int? {
        if (fromRevision.isBlank() || toRevision.isBlank()) {
            LOG.debug("Cannot count commits: a revision is blank")
            return null
        }
        val repository = awaitRepository(project) ?: run {
            LOG.info("Cannot count commits: no Git repository for project ${project.name}")
            return null
        }
        return try {
            val handler = GitLineHandler(project, repository.root, GitCommand.REV_LIST).apply {
                addParameters("--count", "$fromRevision..$toRevision")
            }
            val commandLine = handler.printableCommandLine()
            LOG.debug("Running $commandLine")
            val started = TimeSource.Monotonic.markNow()
            val result = Git.getInstance().runCommand(handler)
            LOG.debug(
                "Finished $commandLine: exitCode=${result.exitCode} in " +
                    "${started.elapsedNow().inWholeMilliseconds} ms"
            )
            if (!result.success()) {
                LOG.debug("rev-list --count $fromRevision..$toRevision failed: exitCode=${result.exitCode}")
                return null
            }
            result.outputAsJoinedString.trim().toIntOrNull()
        } catch (exception: Exception) {
            LOG.debug("Git rev-list --count failed for $fromRevision..$toRevision", exception)
            null
        }
    }

    /**
     * Resolves the first existing `origin/<sourceBranch>` candidate ref after a fetch, so the count compares the local
     * HEAD against the remote tracking branch rather than the local branch tip.
     */
    private fun resolveRemoteSourceRef(repository: GitRepository, sourceBranch: String): String? {
        val remoteName = selectRemote(repository)?.name ?: GitRemote.ORIGIN
        val shortName = sourceBranch.substringAfterLast('/')
        for (candidate in GitBranchRefCandidates.candidates(branchName = shortName, remoteName = remoteName)) {
            if (candidate.startsWith("$remoteName/") && repository.branches.findBranchByName(candidate) != null) {
                return candidate
            }
        }
        return null
    }

    /**
     * Resolves the project's Git repository, tolerating the short window after a work tree project is first opened
     * during which [GitRepositoryManager] has not yet registered the repository.
     *
     * When a pull request is opened in a freshly created work tree, the editor binds (and requests line totals) before
     * Git4Idea finishes registering the repository, so [findRepository] returns `null` on the very first attempt. A
     * manual refresh works only because enough time has passed for registration to complete. Polling briefly here gives
     * the repository a chance to register so the indicator renders on initial open too. Callers run this on a
     * background dispatcher, so the short blocking wait never touches the EDT.
     */
    private fun awaitRepository(project: Project): GitRepository? {
        val deadline = TimeSource.Monotonic.markNow() + REPOSITORY_WAIT_TIMEOUT
        var attempt = 0
        while (true) {
            val repository = findRepository(project)
            if (repository != null) {
                if (attempt > 0) {
                    LOG.debug("Resolved Git repository for project ${project.name} after $attempt retries")
                }
                return repository
            }
            if (project.isDisposed || deadline.hasPassedNow()) {
                return null
            }
            attempt += 1
            Thread.sleep(REPOSITORY_WAIT_POLL_INTERVAL.inWholeMilliseconds)
        }
    }

    private fun findRepository(project: Project): GitRepository? {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val basePath = project.basePath
        val baseDirectory = basePath?.let { path -> LocalFileSystem.getInstance().findFileByPath(path) }
        val repositoryAtBase = baseDirectory?.let { directory -> repositoryManager.getRepositoryForRootQuick(directory) }
        return repositoryAtBase ?: repositoryManager.repositories.singleOrNull()
    }

    private fun resolveBranchRef(repository: GitRepository, branchName: String): String? {
        for (candidate in GitBranchRefCandidates.candidates(branchName = branchName)) {
            if (repository.branches.findBranchByName(candidate) != null) {
                return candidate
            }
        }
        return null
    }

    private fun fetchBranch(project: Project, repository: GitRepository, branchName: String) {
        val remote = selectRemote(repository) ?: run {
            LOG.debug("Repository ${repository.root.path} has no remotes; skipping fetch of branch '$branchName'")
            return
        }
        val shortName = branchName.substringAfterLast('/')
        val fetchHandler = GitLineHandler(project, repository.root, GitCommand.FETCH).apply {
            setUrls(remote.urls)
            addParameters(remote.name, shortName)
        }
        val commandLine = fetchHandler.printableCommandLine()
        LOG.debug("Running $commandLine")
        val started = TimeSource.Monotonic.markNow()
        val result = Git.getInstance().runCommand(fetchHandler)
        LOG.debug("Finished $commandLine: exitCode=${result.exitCode} in ${started.elapsedNow().inWholeMilliseconds} ms")
    }

    private fun selectRemote(repository: GitRepository): GitRemote? {
        val remotes = repository.remotes
        return remotes.firstOrNull { remote -> remote.name == GitRemote.ORIGIN } ?: remotes.firstOrNull()
    }

    companion object {
        private val LOG = logger<BbCloudPullRequestBranchDiffService>()

        // Give Git4Idea time to register a freshly opened work tree's repository before giving up (see awaitRepository).
        private val REPOSITORY_WAIT_TIMEOUT = 5_000.milliseconds
        private val REPOSITORY_WAIT_POLL_INTERVAL = 150.milliseconds
    }
}