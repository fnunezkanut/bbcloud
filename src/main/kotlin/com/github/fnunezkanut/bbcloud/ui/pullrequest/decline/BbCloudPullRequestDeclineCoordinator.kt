package com.github.fnunezkanut.bbcloud.ui.pullrequest.decline

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.BbCloudOpenPullRequestService
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestActionResult
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDeclineResult
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.git.BbCloudWorktreeService
import com.github.fnunezkanut.bbcloud.git.WorktreeRemovalResult
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeMatch
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeResolution
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeResolver
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

internal data class PullRequestDeclineRequest(
    val project: Project,
    val projectBasePath: String,
    val organizationUrl: String,
    val repositorySlug: String,
    val pullRequest: PullRequestSummary,
    val reason: String,
    val configurationService: BbCloudConfigurationService,
    val openPullRequestService: BbCloudOpenPullRequestService
)

internal sealed interface PullRequestDeclineCompletion {
    data class Success(val projectWillClose: Boolean) : PullRequestDeclineCompletion

    data class Failed(val result: PullRequestActionResult.Failed) : PullRequestDeclineCompletion
}

/**
 * A request to clean up the git worktree of a pull request that is already closed (merged or declined) on Bitbucket.
 *
 * Unlike [PullRequestDeclineRequest] this makes no Bitbucket decline call: the pull request is already not Open, so the
 * coordinator only closes the IDE project and removes the worktree.
 */
internal data class ClosedPullRequestWorktreeCleanupRequest(
    val project: Project,
    val projectBasePath: String,
    val organizationUrl: String,
    val repositorySlug: String,
    val pullRequestId: Long,
    val configurationService: BbCloudConfigurationService
)

/** The terminal outcome of a manual closed-PR worktree cleanup, reported back to the editor session. */
internal sealed interface ClosedPullRequestWorktreeCleanupCompletion {
    /** The project was closed and its worktree removed (or reconciled). */
    data object Success : ClosedPullRequestWorktreeCleanupCompletion

    /** Nothing was done: the project base path is not the expected worktree path for this pull request. */
    data object SkippedNotWorktree : ClosedPullRequestWorktreeCleanupCompletion

    /** Cleanup could not complete; the project was left open. [detail] is a short, non-sensitive reason. */
    data class Failed(val detail: String?) : ClosedPullRequestWorktreeCleanupCompletion
}

internal fun interface ProjectCloser {
    suspend fun close(project: Project): Boolean
}

/** Closes a project on the EDT and waits for the platform to finish disposing it. */
internal class IntelliJProjectCloser : ProjectCloser {
    override suspend fun close(project: Project): Boolean {
        if (project.isDisposed || !project.isOpen) {
            return true
        }

        var closeFailure: Throwable? = null
        withContext(Dispatchers.Default) {
            ApplicationManager.getApplication().invokeAndWait {
                try {
                    ProjectManager.getInstance().closeAndDispose(project)
                } catch (exception: Throwable) {
                    closeFailure = exception
                }
            }
        }
        if (closeFailure != null) {
            return false
        }

        repeat(CLOSE_COMPLETION_ATTEMPTS) {
            if (project.isDisposed || !project.isOpen) {
                return true
            }
            delay(CLOSE_COMPLETION_DELAY_MS)
        }
        return project.isDisposed || !project.isOpen
    }

    companion object {
        private const val CLOSE_COMPLETION_ATTEMPTS = 100
        private const val CLOSE_COMPLETION_DELAY_MS = 50L
    }
}

/**
 * The resolution of a worktree project cleanup: whether the project base path really is the pull-request worktree, and
 * if so whether its source checkout could be resolved so the worktree can be removed.
 */
internal sealed interface WorktreeCleanupResolution {
    /** The project base path is not the expected worktree path for this pull request; nothing should be removed. */
    data object NotAMatchingWorktree : WorktreeCleanupResolution

    /** The project is the expected worktree, but its source checkout could not be resolved. */
    data class SourceUnavailable(val expectedPath: Path) : WorktreeCleanupResolution

    /** Both the source checkout and the (matching) worktree target resolved and may be removed. */
    data class Resolved(val resolution: PullRequestWorktreeResolution.Resolved) : WorktreeCleanupResolution
}

/**
 * Shared "close the IDE project, then remove its pull-request worktree" logic used by both decline cleanup and manual
 * closed-PR cleanup. It never calls any Bitbucket API: the pull request is expected to already be closed when a caller
 * reaches the cleanup step.
 *
 * Resolution (via [resolveCleanup]) proves the project base path is exactly the expected
 * `$workTreeDirectory/$repoSlug--pr-$prNumber` worktree ([PullRequestWorktreeMatch]) and that its source checkout
 * resolves ([PullRequestWorktreeResolver]) before anything is removed. A single [cleanupInProgress] dedupe set is
 * shared across decline and manual cleanup so the same worktree path is never removed twice concurrently.
 */
internal class WorktreeProjectCleanupOrchestrator(private val projectCloser: ProjectCloser, private val worktreeService: BbCloudWorktreeService) {
    private val cleanupInProgress = ConcurrentHashMap.newKeySet<Path>()

    fun resolveCleanup(
        projectBasePath: String,
        organizationUrl: String,
        repositorySlug: String,
        pullRequestId: Long,
        configurationService: BbCloudConfigurationService
    ): WorktreeCleanupResolution {
        val expectedPath = PullRequestWorktreeMatch.expectedWorktreePath(
            projectBasePath = projectBasePath,
            workTreeDirectory = configurationService.workTreeDirectoryOrNull(),
            currentRepositorySlug = repositorySlug,
            currentPullRequestId = pullRequestId,
            declinedRepositorySlug = repositorySlug,
            declinedPullRequestId = pullRequestId
        ) ?: return WorktreeCleanupResolution.NotAMatchingWorktree

        val workspace = BitbucketRepositoryUrls.workspaceFromOrganizationUrl(organizationUrl)
        val resolution = workspace?.let {
            PullRequestWorktreeResolver.resolve(
                mappings = configurationService.watchedProjectsForCurrentWorkspaceWithLocalPaths(),
                workTreeDirectory = configurationService.workTreeDirectoryOrNull(),
                projectsDirectory = configurationService.projectsDirectoryOrNull(),
                workspace = it,
                repoSlug = repositorySlug,
                prNumber = pullRequestId
            )
        }
        return when (resolution) {
            is PullRequestWorktreeResolution.Resolved -> {
                if (resolution.worktreePath == expectedPath) {
                    WorktreeCleanupResolution.Resolved(resolution)
                } else {
                    WorktreeCleanupResolution.SourceUnavailable(expectedPath)
                }
            }
            else -> {
                WorktreeCleanupResolution.SourceUnavailable(expectedPath)
            }
        }
    }

    /**
     * Closes [project] then removes the resolved worktree. The dedupe set guards against the same worktree path being
     * cleaned up twice concurrently (for example a decline cleanup racing a manual cleanup).
     *
     * @return the terminal [CleanupOutcome] describing what happened, so callers can notify and re-enable their UI.
     */
    suspend fun cleanupWorktreeProject(project: Project, pullRequestId: Long, resolution: WorktreeCleanupResolution.Resolved): CleanupOutcome {
        val targetPath = resolution.resolution.worktreePath.toAbsolutePath().normalize()
        if (!cleanupInProgress.add(targetPath)) {
            LOG.info("Skipped duplicate cleanup for closed pull request #$pullRequestId at $targetPath")
            return CleanupOutcome.Skipped
        }
        try {
            LOG.info("Scheduling cleanup for closed pull request #$pullRequestId at $targetPath")
            if (!projectCloser.close(project)) {
                LOG.warn("Could not close the project for closed pull request #$pullRequestId at $targetPath")
                return CleanupOutcome.Failed(targetPath = targetPath, detail = "the project could not be closed")
            }
            return when (
                val removal = worktreeService.removeWorktree(
                    sourceCheckoutRoot = resolution.resolution.sourceCheckoutPath,
                    worktreePath = targetPath,
                    workTreeDirectory = targetPath.parent
                )
            ) {
                WorktreeRemovalResult.Success -> {
                    LOG.debug("Removed closed pull request worktree at $targetPath")
                    CleanupOutcome.Removed(targetPath = targetPath)
                }
                is WorktreeRemovalResult.Failed -> {
                    LOG.warn("Could not remove closed pull request worktree at $targetPath", removal.cause)
                    CleanupOutcome.Failed(targetPath = targetPath, detail = removal.output)
                }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.warn("Could not clean up closed pull request worktree at $targetPath", exception)
            return CleanupOutcome.Failed(targetPath = targetPath, detail = exception.message)
        } finally {
            cleanupInProgress.remove(targetPath)
        }
    }

    /** The result of closing a project and removing its worktree. */
    internal sealed interface CleanupOutcome {
        /** The worktree was removed (or reconciled). [targetPath] is the normalized worktree path. */
        data class Removed(val targetPath: Path) : CleanupOutcome

        /** A concurrent cleanup already held this worktree path; nothing was done by this call. */
        data object Skipped : CleanupOutcome

        /** Cleanup failed after (or while) closing the project. [detail] is a short, non-sensitive reason. */
        data class Failed(val targetPath: Path, val detail: String?) : CleanupOutcome
    }

    companion object {
        private val LOG = logger<WorktreeProjectCleanupOrchestrator>()
    }
}

/**
 * Coordinates the application-scoped part of declining a pull request and removing its worktree.
 *
 * [projectCloser] and [worktreeService] build the default [cleanupOrchestrator] when one is not supplied (the path
 * tests use); the coordinator supplies a shared [cleanupOrchestrator] so decline and closed-PR cleanup dedupe together.
 */
internal class PullRequestDeclineOrchestrator(
    projectCloser: ProjectCloser,
    worktreeService: BbCloudWorktreeService,
    private val scope: CoroutineScope,
    private val cleanupOrchestrator: WorktreeProjectCleanupOrchestrator = WorktreeProjectCleanupOrchestrator(
        projectCloser = projectCloser,
        worktreeService = worktreeService
    )
) {
    fun launch(request: PullRequestDeclineRequest, onCompleted: (PullRequestDeclineCompletion) -> Unit) {
        scope.launch {
            try {
                val cleanupResolution = cleanupOrchestrator.resolveCleanup(
                    projectBasePath = request.projectBasePath,
                    organizationUrl = request.organizationUrl,
                    repositorySlug = request.repositorySlug,
                    pullRequestId = request.pullRequest.id,
                    configurationService = request.configurationService
                )
                when (
                    val declineResult = request.openPullRequestService.declinePullRequest(
                        projectBasePath = request.projectBasePath,
                        organizationUrl = request.organizationUrl,
                        pullRequest = request.pullRequest,
                        reason = request.reason
                    )
                ) {
                    is PullRequestDeclineResult.Failed -> {
                        onCompleted(PullRequestDeclineCompletion.Failed(declineResult.failure))
                    }
                    is PullRequestDeclineResult.Success -> {
                        declineResult.commentFailure?.let { failure ->
                            notifyCommentFailure(request = request, failure = failure)
                        }
                        if (cleanupResolution is WorktreeCleanupResolution.SourceUnavailable) {
                            notifySourceUnavailable(request = request, targetPath = cleanupResolution.expectedPath)
                        }
                        val canCloseAndRemove = cleanupResolution is WorktreeCleanupResolution.Resolved
                        onCompleted(PullRequestDeclineCompletion.Success(projectWillClose = canCloseAndRemove))
                        if (cleanupResolution is WorktreeCleanupResolution.Resolved) {
                            cleanup(request = request, resolution = cleanupResolution)
                        }
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                LOG.warn("Decline orchestration failed for pull request #${request.pullRequest.id}", exception)
                onCompleted(
                    PullRequestDeclineCompletion.Failed(
                        result = PullRequestActionResult.Failed(cause = exception)
                    )
                )
            }
        }
    }

    private suspend fun cleanup(request: PullRequestDeclineRequest, resolution: WorktreeCleanupResolution.Resolved) {
        val outcome = cleanupOrchestrator.cleanupWorktreeProject(
            project = request.project,
            pullRequestId = request.pullRequest.id,
            resolution = resolution
        )
        if (outcome is WorktreeProjectCleanupOrchestrator.CleanupOutcome.Failed) {
            notifyCleanupFailure(request = request, targetPath = outcome.targetPath, detail = outcome.detail)
        }
    }

    private fun notifyCommentFailure(request: PullRequestDeclineRequest, failure: PullRequestActionResult.Failed) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                BbCloudBundle.message("main.branch.decline.commentFailed.title", request.pullRequest.id.toString()),
                failure.message ?: BbCloudBundle.message("main.branch.decline.commentFailed.message"),
                NotificationType.WARNING
            )
            .notify(null)
    }

    private fun notifyCleanupFailure(request: PullRequestDeclineRequest, targetPath: Path, detail: String?) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                BbCloudBundle.message("main.branch.decline.cleanupFailed.title", request.pullRequest.id.toString()),
                BbCloudBundle.message("main.branch.decline.cleanupFailed.message", targetPath, detail.orEmpty()),
                NotificationType.WARNING
            )
            .notify(null)
    }

    private fun notifySourceUnavailable(request: PullRequestDeclineRequest, targetPath: Path) {
        LOG.warn("Could not resolve the source checkout for declined pull request #${request.pullRequest.id} at $targetPath")
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                BbCloudBundle.message("main.branch.decline.cleanupFailed.title", request.pullRequest.id.toString()),
                BbCloudBundle.message("main.branch.decline.sourceUnavailable", targetPath),
                NotificationType.WARNING
            )
            .notify(null)
    }

    companion object {
        private val LOG = logger<PullRequestDeclineOrchestrator>()
        private const val NOTIFICATION_GROUP_ID = "BbCloud"
    }
}

/**
 * Coordinates the application-scoped part of cleaning up the worktree of an already-closed pull request, from the
 * closed-PR takeover view. Shares the "close project then remove worktree" path and the dedupe set with decline
 * cleanup via [WorktreeProjectCleanupOrchestrator].
 */
internal class ClosedPullRequestWorktreeCleanupOrchestrator(
    private val scope: CoroutineScope,
    private val cleanupOrchestrator: WorktreeProjectCleanupOrchestrator
) {
    /**
     * Runs the whole closed-PR cleanup on the application scope so it outlives the editor session being closed. The
     * project-scoped modal (decision 7d) is shown from here too: when the resolved path closes the project the modal
     * dismisses with it, but the git removal continues on this scope regardless. [onCompleted] reports the terminal
     * outcome so the caller can re-enable its button on the paths that leave the project open (decisions 4a, 5a).
     */
    fun launch(request: ClosedPullRequestWorktreeCleanupRequest, onCompleted: (ClosedPullRequestWorktreeCleanupCompletion) -> Unit) {
        // The actual close-then-remove runs as an independent child of the application scope so it survives both the
        // project disposing mid-close and the project-scoped modal dismissing with it (decision 7d). The modal and the
        // completion reporting each only await this one job; if the modal's awaiting coroutine is cancelled when the
        // project closes, the cleanup job keeps running to completion on the application scope.
        val cleanupJob = scope.async {
            runCleanup(request = request)
        }
        // Reports the outcome back to the caller once the cleanup job settles, independent of the modal's lifetime.
        scope.launch {
            val completion = try {
                cleanupJob.await()
            } catch (exception: CancellationException) {
                LOG.info("Closed pull request #${request.pullRequestId} worktree cleanup was cancelled")
                ClosedPullRequestWorktreeCleanupCompletion.Failed(detail = null)
            } catch (exception: Exception) {
                LOG.warn("Closed pull request #${request.pullRequestId} worktree cleanup failed", exception)
                ClosedPullRequestWorktreeCleanupCompletion.Failed(detail = exception.message)
            }
            onCompleted(completion)
        }
        // Shows the project-scoped modal for the duration; dismissing it (for example when the project closes) does not
        // stop the cleanup job above.
        scope.launch {
            try {
                withModalProgress(
                    owner = ModalTaskOwner.project(request.project),
                    title = BbCloudBundle.message("main.branch.closed.cleanup.modalTitle"),
                    cancellation = TaskCancellation.cancellable()
                ) {
                    cleanupJob.join()
                }
            } catch (exception: CancellationException) {
                LOG.debug("Closed pull request #${request.pullRequestId} cleanup modal was dismissed while work continues")
            } catch (exception: Exception) {
                LOG.warn("Closed pull request #${request.pullRequestId} cleanup modal failed", exception)
            }
        }
    }

    private suspend fun runCleanup(request: ClosedPullRequestWorktreeCleanupRequest): ClosedPullRequestWorktreeCleanupCompletion {
        val resolution = cleanupOrchestrator.resolveCleanup(
            projectBasePath = request.projectBasePath,
            organizationUrl = request.organizationUrl,
            repositorySlug = request.repositorySlug,
            pullRequestId = request.pullRequestId,
            configurationService = request.configurationService
        )
        return when (resolution) {
            is WorktreeCleanupResolution.NotAMatchingWorktree -> {
                LOG.info(
                    "Skipped closed pull request #${request.pullRequestId} cleanup: " +
                        "${request.projectBasePath} is not its work tree"
                )
                ClosedPullRequestWorktreeCleanupCompletion.SkippedNotWorktree
            }
            is WorktreeCleanupResolution.SourceUnavailable -> {
                notifySourceUnavailable(request = request, targetPath = resolution.expectedPath)
                ClosedPullRequestWorktreeCleanupCompletion.Failed(
                    detail = BbCloudBundle.message("main.branch.closed.cleanup.sourceUnavailable")
                )
            }
            is WorktreeCleanupResolution.Resolved -> {
                when (
                    val outcome = cleanupOrchestrator.cleanupWorktreeProject(
                        project = request.project,
                        pullRequestId = request.pullRequestId,
                        resolution = resolution
                    )
                ) {
                    is WorktreeProjectCleanupOrchestrator.CleanupOutcome.Removed -> {
                        ClosedPullRequestWorktreeCleanupCompletion.Success
                    }
                    WorktreeProjectCleanupOrchestrator.CleanupOutcome.Skipped -> {
                        ClosedPullRequestWorktreeCleanupCompletion.SkippedNotWorktree
                    }
                    is WorktreeProjectCleanupOrchestrator.CleanupOutcome.Failed -> {
                        notifyCleanupFailure(
                            pullRequestId = request.pullRequestId,
                            targetPath = outcome.targetPath,
                            detail = outcome.detail
                        )
                        ClosedPullRequestWorktreeCleanupCompletion.Failed(detail = outcome.detail)
                    }
                }
            }
        }
    }

    private fun notifyCleanupFailure(pullRequestId: Long, targetPath: Path, detail: String?) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                BbCloudBundle.message("main.branch.closed.cleanup.failed.title", pullRequestId.toString()),
                BbCloudBundle.message("main.branch.closed.cleanup.failed.message", targetPath, detail.orEmpty()),
                NotificationType.WARNING
            )
            .notify(null)
    }

    private fun notifySourceUnavailable(request: ClosedPullRequestWorktreeCleanupRequest, targetPath: Path) {
        LOG.warn("Could not resolve the source checkout for closed pull request #${request.pullRequestId} at $targetPath")
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                BbCloudBundle.message("main.branch.closed.cleanup.failed.title", request.pullRequestId.toString()),
                BbCloudBundle.message("main.branch.closed.cleanup.sourceUnavailable"),
                NotificationType.WARNING
            )
            .notify(null)
    }

    companion object {
        private val LOG = logger<ClosedPullRequestWorktreeCleanupOrchestrator>()
        private const val NOTIFICATION_GROUP_ID = "BbCloud"
    }
}

/** Application-lifetime owner for decline and closed-PR cleanup; it outlives the editor session being closed. */
class BbCloudPullRequestDeclineCoordinator : Disposable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val projectCloser = IntelliJProjectCloser()
    private val cleanupOrchestrator = WorktreeProjectCleanupOrchestrator(
        projectCloser = projectCloser,
        worktreeService = BbCloudWorktreeService.getInstance()
    )
    private val orchestrator = PullRequestDeclineOrchestrator(
        projectCloser = projectCloser,
        worktreeService = BbCloudWorktreeService.getInstance(),
        scope = scope,
        cleanupOrchestrator = cleanupOrchestrator
    )
    private val closedCleanupOrchestrator = ClosedPullRequestWorktreeCleanupOrchestrator(
        scope = scope,
        cleanupOrchestrator = cleanupOrchestrator
    )

    internal fun launch(request: PullRequestDeclineRequest, onCompleted: (PullRequestDeclineCompletion) -> Unit) {
        orchestrator.launch(request = request, onCompleted = onCompleted)
    }

    internal fun launchClosedPullRequestWorktreeCleanup(
        request: ClosedPullRequestWorktreeCleanupRequest,
        onCompleted: (ClosedPullRequestWorktreeCleanupCompletion) -> Unit
    ) {
        closedCleanupOrchestrator.launch(request = request, onCompleted = onCompleted)
    }

    override fun dispose() {
        scope.cancel()
    }

    companion object {
        fun getInstance(): BbCloudPullRequestDeclineCoordinator {
            return ApplicationManager.getApplication().service()
        }
    }
}