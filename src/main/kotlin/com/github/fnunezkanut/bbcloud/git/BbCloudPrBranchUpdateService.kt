package com.github.fnunezkanut.bbcloud.git

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource

/**
 * Updates the local pull-request branch from its remote by running `git pull origin <sourceBranch>` (decision 2d).
 *
 * The update aborts when the work tree has uncommitted changes (decision 9a) so the pull never has to stash or
 * overwrite local work, and surfaces the sanitized git output on conflict or any other failure (decision 6a) rather
 * than driving an in-IDE merge. The service deliberately uses a plain [GitLineHandler] bound to the project instead of
 * the `@ApiStatus.Internal` `GitUpdateProcess`.
 */
class BbCloudPrBranchUpdateService {
    /**
     * Pulls [sourceBranch] from the remote into the currently checked-out branch.
     *
     * The caller guarantees, via the banner's branch-match gate (decision 3b), that the work tree is checked out on
     * [sourceBranch]; the pull therefore fast-forwards or merges the remote source branch into the current branch.
     */
    suspend fun updateBranch(project: Project, sourceBranch: String): PrBranchUpdateResult {
        val repository = findRepository(project)
        if (repository == null) {
            LOG.info("No Git repository found for project ${project.name}; cannot update branch '$sourceBranch'")
            return PrBranchUpdateResult.Failed(
                output = BbCloudBundle.message("main.checkout.noRepository")
            )
        }
        val remoteName = selectRemote(repository)?.name ?: GitRemote.ORIGIN
        val checkoutRoot = repository.root.toNioPath()
        return try {
            withBackgroundProgress(project, BbCloudBundle.message("main.branch.localBehindRemote.progressTitle")) {
                coroutineToIndicator { _ ->
                    runUpdate(
                        project = project,
                        checkoutRoot = checkoutRoot,
                        remoteName = remoteName,
                        sourceBranch = sourceBranch
                    )
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.debug("Update of branch '$sourceBranch' failed unexpectedly", exception)
            PrBranchUpdateResult.Failed(output = exception.message.orEmpty(), cause = exception)
        }
    }

    private fun runUpdate(project: Project, checkoutRoot: Path, remoteName: String, sourceBranch: String): PrBranchUpdateResult {
        val hasUncommittedChanges = runStatusProbe(project = project, checkoutRoot = checkoutRoot)
        if (hasUncommittedChanges == null) {
            LOG.info("Cannot update branch '$sourceBranch': the work tree status could not be read")
            return PrBranchUpdateResult.Failed(output = BbCloudBundle.message("main.checkout.noRepository"))
        }
        if (hasUncommittedChanges) {
            LOG.info("Aborting update of branch '$sourceBranch': the work tree has uncommitted changes")
            return PrBranchUpdateResult.DirtyWorktree
        }
        val shortName = sourceBranch.substringAfterLast('/')
        val pullHandler = GitLineHandler(project, checkoutRoot, GitCommand.PULL).apply {
            addParameters(remoteName, shortName)
        }
        val commandLine = pullHandler.printableCommandLine()
        LOG.debug("Running $commandLine")
        val started = TimeSource.Monotonic.markNow()
        val result = Git.getInstance().runCommand(pullHandler)
        LOG.debug("Finished $commandLine: exitCode=${result.exitCode} in ${started.elapsedNow().inWholeMilliseconds} ms")
        if (!result.success()) {
            LOG.debug("git pull $remoteName $shortName failed: exitCode=${result.exitCode}")
            return PrBranchUpdateResult.Failed(
                output = GitCommandOutput.format(
                    commandLine = commandLine,
                    output = result.outputAsJoinedString,
                    errorOutput = result.errorOutputAsJoinedString
                )
            )
        }
        refreshWorkTree(project = project, checkoutRoot = checkoutRoot)
        return PrBranchUpdateResult.Success
    }

    private fun runStatusProbe(project: Project, checkoutRoot: Path): Boolean? {
        val handler = GitLineHandler(project, checkoutRoot, GitCommand.STATUS).apply {
            addParameters("--porcelain", "--untracked-files=normal")
        }
        val commandLine = handler.printableCommandLine()
        LOG.debug("Running $commandLine")
        val started = TimeSource.Monotonic.markNow()
        val result = Git.getInstance().runCommand(handler)
        LOG.debug("Finished $commandLine: exitCode=${result.exitCode} in ${started.elapsedNow().inWholeMilliseconds} ms")
        if (!result.success()) {
            LOG.warn("git status failed with exitCode=${result.exitCode}")
            return null
        }
        return GitStatusOutput.hasUncommittedChanges(result.outputAsJoinedString)
    }

    private fun refreshWorkTree(project: Project, checkoutRoot: Path) {
        val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(checkoutRoot) ?: return
        GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(virtualFile)?.update()
        VfsUtil.markDirtyAndRefresh(false, true, false, virtualFile)
    }

    private fun findRepository(project: Project): GitRepository? {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val basePath = project.basePath
        val baseDirectory = basePath?.let { path -> LocalFileSystem.getInstance().findFileByPath(path) }
        val repositoryAtBase = baseDirectory?.let { directory -> repositoryManager.getRepositoryForRootQuick(directory) }
        return repositoryAtBase ?: repositoryManager.repositories.singleOrNull()
    }

    private fun selectRemote(repository: GitRepository): GitRemote? {
        val remotes = repository.remotes
        return remotes.firstOrNull { remote -> remote.name == GitRemote.ORIGIN } ?: remotes.firstOrNull()
    }

    companion object {
        private val LOG = logger<BbCloudPrBranchUpdateService>()
    }
}

/** The outcome of a local pull-request branch update. */
sealed interface PrBranchUpdateResult {
    data object Success : PrBranchUpdateResult

    /** The work tree had uncommitted changes, so the pull was not attempted (decision 9a). */
    data object DirtyWorktree : PrBranchUpdateResult

    /** The pull failed or hit a conflict; [output] is the sanitized git output for the failure notification. */
    data class Failed(val output: String, val cause: Throwable? = null) : PrBranchUpdateResult
}