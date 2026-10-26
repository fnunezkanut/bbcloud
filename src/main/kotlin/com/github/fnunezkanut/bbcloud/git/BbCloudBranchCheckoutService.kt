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

class BbCloudBranchCheckoutService {
    suspend fun checkout(project: Project, branchName: String): BranchCheckoutResult {
        val repository = findRepository(project)
        if (repository == null) {
            LOG.info("No Git repository found for project ${project.name}; cannot check out $branchName")
            return BranchCheckoutResult.Failed(
                branchName = branchName,
                output = BbCloudBundle.message("main.checkout.noRepository")
            )
        }
        val remoteUrl = selectRemote(repository)?.urls?.firstOrNull()
        val failureOutput = fetchAndCheckout(
            project = project,
            progressTitle = BbCloudBundle.message("main.checkout.progressTitle"),
            checkoutRoot = repository.root.toNioPath(),
            remoteUrl = remoteUrl,
            fetchRef = branchName,
            checkoutTarget = branchName
        )
        return if (failureOutput == null) {
            BranchCheckoutResult.Success(branchName = branchName)
        } else {
            BranchCheckoutResult.Failed(
                branchName = branchName,
                output = failureOutput.output,
                cause = failureOutput.cause
            )
        }
    }

    /** Returns the current branch name and whether the working tree has uncommitted changes. */
    suspend fun workingTreeStatus(project: Project): WorkingTreeStatus? {
        val repository = findRepository(project)
        if (repository == null) {
            LOG.info("No Git repository found for project ${project.name}; skipping the branch switch warning")
            return null
        }
        return readWorkingTreeStatus(project = project, checkoutRoot = repository.root.toNioPath())
    }

    /**
     * Reads the working-tree state at [checkoutRoot] without depending on a [GitRepository] being discovered for the
     * project. A newly opened project may not have its repository registered yet, so this method runs the Git probes
     * directly against the checkout path and returns null when either probe fails.
     */
    suspend fun readWorkingTreeStatus(project: Project, checkoutRoot: Path): WorkingTreeStatus? {
        return try {
            withBackgroundProgress(project, BbCloudBundle.message("main.checkout.inspectProgressTitle")) {
                coroutineToIndicator { _ ->
                    runWorkingTreeProbes(project = project, checkoutRoot = checkoutRoot)
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.debug("Working tree probes failed unexpectedly", exception)
            null
        }
    }

    /**
     * Fetches [branchName] from the remote at [remoteUrl] (or skips the fetch when the URL is null) and checks out
     * [branchName] in the local checkout at [checkoutRoot], refreshing the IDE's VCS state afterwards.
     */
    suspend fun fetchAndCheckoutBranch(project: Project, checkoutRoot: Path, remoteUrl: String?, branchName: String): BranchCheckoutResult {
        val failureOutput = fetchAndCheckout(
            project = project,
            progressTitle = BbCloudBundle.message("main.checkout.progressTitle"),
            checkoutRoot = checkoutRoot,
            remoteUrl = remoteUrl,
            fetchRef = branchName,
            checkoutTarget = branchName
        )
        return if (failureOutput == null) {
            BranchCheckoutResult.Success(branchName = branchName)
        } else {
            BranchCheckoutResult.Failed(
                branchName = branchName,
                output = failureOutput.output,
                cause = failureOutput.cause
            )
        }
    }

    /** Returns null on success. */
    private suspend fun fetchAndCheckout(
        project: Project,
        progressTitle: String,
        checkoutRoot: Path,
        remoteUrl: String?,
        fetchRef: String,
        checkoutTarget: String
    ): CheckoutFailure? {
        val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(checkoutRoot)
        if (virtualFile == null) {
            LOG.info("No Git repository found for checkout root $checkoutRoot; cannot check out $checkoutTarget")
            return CheckoutFailure(output = BbCloudBundle.message("main.checkout.noRepository"))
        }
        return try {
            withBackgroundProgress(project, progressTitle) {
                coroutineToIndicator { _ ->
                    runCheckout(
                        project = project,
                        checkoutRoot = checkoutRoot,
                        remoteUrl = remoteUrl,
                        fetchRef = fetchRef,
                        checkoutTarget = checkoutTarget
                    )
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.debug("Checkout of $checkoutTarget failed unexpectedly", exception)
            CheckoutFailure(output = exception.message.orEmpty(), cause = exception)
        }
    }

    private fun findRepository(project: Project): GitRepository? {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val basePath = project.basePath
        val baseDirectory = basePath?.let { path -> LocalFileSystem.getInstance().findFileByPath(path) }
        val repositoryAtBase = baseDirectory?.let { directory -> repositoryManager.getRepositoryForRootQuick(directory) }
        return repositoryAtBase ?: repositoryManager.repositories.singleOrNull()
    }

    private fun runWorkingTreeProbes(project: Project, checkoutRoot: Path): WorkingTreeStatus? {
        val currentBranch = runRevParseProbe(project = project, checkoutRoot = checkoutRoot)
        val hasUncommittedChanges = runStatusProbe(project = project, checkoutRoot = checkoutRoot)
        if (hasUncommittedChanges == null) {
            return null
        }
        return WorkingTreeStatus(currentBranch = currentBranch, hasUncommittedChanges = hasUncommittedChanges)
    }

    private fun runRevParseProbe(project: Project, checkoutRoot: Path): String? {
        val handler = GitLineHandler(project, checkoutRoot, GitCommand.REV_PARSE).apply {
            addParameters("--abbrev-ref", "HEAD")
        }
        val commandLine = handler.printableCommandLine()
        LOG.debug("Running $commandLine")
        val started = TimeSource.Monotonic.markNow()
        val result = Git.getInstance().runCommand(handler)
        LOG.debug("Finished $commandLine: exitCode=${result.exitCode} in ${started.elapsedNow().inWholeMilliseconds} ms")
        if (!result.success()) {
            LOG.debug("rev-parse failed with exitCode=${result.exitCode}")
            return null
        }
        val output = result.outputAsJoinedString.trim()
        return if (output == "HEAD") null else output
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

    private fun runCheckout(project: Project, checkoutRoot: Path, remoteUrl: String?, fetchRef: String, checkoutTarget: String): CheckoutFailure? {
        if (remoteUrl != null) {
            val fetchHandler = GitLineHandler(project, checkoutRoot, GitCommand.FETCH).apply {
                setUrls(listOf(remoteUrl))
                addParameters("origin", fetchRef)
            }
            val fetchFailure = runHandler(handler = fetchHandler)
            if (fetchFailure != null) {
                return fetchFailure
            }
        } else {
            LOG.debug("No origin remote URL resolved for checkout root $checkoutRoot; skipping fetch")
        }

        val checkoutHandler = GitLineHandler(project, checkoutRoot, GitCommand.CHECKOUT).apply {
            addParameters(checkoutTarget)
        }
        val checkoutFailure = runHandler(handler = checkoutHandler)
        if (checkoutFailure != null) {
            return checkoutFailure
        }

        val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(checkoutRoot)
        if (virtualFile != null) {
            GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(virtualFile)?.update()
            VfsUtil.markDirtyAndRefresh(false, true, false, virtualFile)
        }
        return null
    }

    private fun selectRemote(repository: GitRepository): GitRemote? {
        val remotes = repository.remotes
        return remotes.firstOrNull { remote -> remote.name == GitRemote.ORIGIN } ?: remotes.firstOrNull()
    }

    private fun runHandler(handler: GitLineHandler): CheckoutFailure? {
        val commandLine = handler.printableCommandLine()
        LOG.debug("Running $commandLine")
        val started = TimeSource.Monotonic.markNow()
        val result = Git.getInstance().runCommand(handler)
        LOG.debug("Finished $commandLine: exitCode=${result.exitCode} in ${started.elapsedNow().inWholeMilliseconds} ms")
        if (result.success()) {
            return null
        }
        return CheckoutFailure(
            output = GitCommandOutput.format(
                commandLine = commandLine,
                output = result.outputAsJoinedString,
                errorOutput = result.errorOutputAsJoinedString
            )
        )
    }

    private data class CheckoutFailure(val output: String, val cause: Throwable? = null)

    companion object {
        private val LOG = logger<BbCloudBranchCheckoutService>()
    }
}