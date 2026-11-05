package com.github.fnunezkanut.bbcloud.ui.worktree

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.git.BbCloudWorktreeService
import com.github.fnunezkanut.bbcloud.project.WatchedWorktreeRemovalOutcome
import com.github.fnunezkanut.bbcloud.ui.pullrequest.decline.BbCloudPullRequestDeclineCoordinator
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * The shared "remove a PR work tree" flow used by the Watched Projects and Recent Projects tabs.
 *
 * It refuses while pruning runs or the work tree is being opened, ignores a second request for a work tree that is
 * already being removed (in any tab or window, the guard is application wide), optionally asks for confirmation, and
 * then hands over to [BbCloudPullRequestDeclineCoordinator], which closes the IDE window on the work tree and removes it.
 * Notifications for every outcome are shown here.
 */
class PullRequestWorktreeRemovalLauncher(
    private val project: Project,
    private val configurationService: BbCloudConfigurationService,
    private val coroutineScope: CoroutineScope,
    private val worktreeOpener: PullRequestWorktreeOpener,
    private val isPruneInProgress: () -> Boolean
) {
    /**
     * Starts the removal; call it on the EDT. [onFinished] runs on the EDT after the removal ended and the outcome was
     * reported, unless the project closed in the meantime.
     */
    fun remove(sourceCheckoutRoot: Path, worktreePath: Path, prNumber: Long, askForConfirmation: Boolean, onFinished: () -> Unit) {
        val normalizedWorktree = worktreePath.toAbsolutePath().normalize()
        if (isPruneInProgress()) {
            LOG.info("Cannot remove the work tree of PR #$prNumber: pruning is running")
            notify(
                contentKey = "workspace.watchedProjects.worktree.remove.pruneBusy",
                contentArgs = emptyArray(),
                type = NotificationType.WARNING
            )
            return
        }
        if (normalizedWorktree in worktreeOpener.worktreesBeingOpened() || worktreePath in worktreeOpener.worktreesBeingOpened()) {
            LOG.info("Cannot remove the work tree of PR #$prNumber: it is being opened")
            notify(
                contentKey = "workspace.watchedProjects.worktree.remove.inUse",
                contentArgs = arrayOf<Any>(prNumber),
                type = NotificationType.WARNING
            )
            return
        }
        val workTreeDirectory = configurationService.workTreeDirectoryOrNull()
        if (workTreeDirectory == null) {
            LOG.info("Cannot remove the work tree of PR #$prNumber: no projects directory")
            notify(
                contentKey = "workspace.watchedProjects.worktree.open.removeFailed",
                contentArgs = arrayOf<Any>(prNumber),
                type = NotificationType.ERROR
            )
            return
        }
        if (!worktreesBeingRemoved.add(normalizedWorktree)) {
            LOG.info("Ignoring a repeated remove request for the work tree of PR #$prNumber")
            return
        }
        coroutineScope.launch {
            var removalStarted = false
            try {
                if (askForConfirmation && !askToRemove(worktreePath = normalizedWorktree)) {
                    LOG.info("User cancelled removal of the work tree of PR #$prNumber")
                    return@launch
                }
                LOG.info("User confirmed removal of the work tree of PR #$prNumber")
                removalStarted = true
                start(
                    sourceCheckoutRoot = sourceCheckoutRoot,
                    worktreePath = normalizedWorktree,
                    prNumber = prNumber,
                    workTreeDirectory = Path.of(workTreeDirectory),
                    onFinished = onFinished
                )
            } finally {
                if (!removalStarted) {
                    worktreesBeingRemoved.remove(normalizedWorktree)
                }
            }
        }
    }

    /** Asks whether to remove the work tree and warns when it has uncommitted or untracked changes. True when the user chooses Remove. */
    private suspend fun askToRemove(worktreePath: Path): Boolean {
        val hasChanges = hasUncommittedChanges(worktreePath = worktreePath)
        val baseMessage = BbCloudBundle.message("workspace.watchedProjects.worktree.remove.confirm.message", worktreePath.toString())
        val message = if (hasChanges) {
            baseMessage + "\n\n" + BbCloudBundle.message("workspace.watchedProjects.worktree.remove.confirm.uncommitted")
        } else {
            baseMessage
        }
        return withContext(Dispatchers.EDT) {
            Messages.showYesNoDialog(
                project,
                message,
                BbCloudBundle.message("workspace.watchedProjects.worktree.remove.confirm.title"),
                BbCloudBundle.message("workspace.watchedProjects.worktree.remove.confirm.yes"),
                BbCloudBundle.message("workspace.watchedProjects.worktree.remove.confirm.no"),
                Messages.getWarningIcon()
            ) == Messages.YES
        }
    }

    private suspend fun hasUncommittedChanges(worktreePath: Path): Boolean {
        return try {
            val exists = withContext(Dispatchers.IO) { Files.isDirectory(worktreePath) }
            exists && BbCloudWorktreeService.getInstance().hasUncommittedChanges(worktreePath = worktreePath)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.debug("Could not read the status of a work tree before removing it", exception)
            false
        }
    }

    /**
     * Starts the removal and returns without waiting. The guard is released when the removal completes, so a second
     * click is still ignored until then. If the start itself throws, the guard is released here.
     */
    private fun start(sourceCheckoutRoot: Path, worktreePath: Path, prNumber: Long, workTreeDirectory: Path, onFinished: () -> Unit) {
        try {
            BbCloudPullRequestDeclineCoordinator.getInstance().launchWatchedWorktreeRemoval(
                project = project,
                sourceCheckoutRoot = sourceCheckoutRoot,
                worktreePath = worktreePath,
                workTreeDirectory = workTreeDirectory,
                onCompleted = { outcome ->
                    worktreesBeingRemoved.remove(worktreePath)
                    ApplicationManager.getApplication().invokeLater {
                        finish(prNumber = prNumber, outcome = outcome, onFinished = onFinished)
                    }
                }
            )
        } catch (exception: Exception) {
            worktreesBeingRemoved.remove(worktreePath)
            throw exception
        }
    }

    /** Reports the outcome on the EDT. When the project has already closed there is no window to notify, so only the log is written. */
    private fun finish(prNumber: Long, outcome: WatchedWorktreeRemovalOutcome, onFinished: () -> Unit) {
        if (project.isDisposed) {
            LOG.info("Work tree removal of PR #$prNumber finished after the project closed")
            return
        }
        when (outcome) {
            WatchedWorktreeRemovalOutcome.Removed -> {
                LOG.info("Removed the work tree of PR #$prNumber")
                notify(
                    contentKey = "workspace.watchedProjects.worktree.open.removed",
                    contentArgs = arrayOf<Any>(prNumber),
                    type = NotificationType.INFORMATION
                )
            }
            WatchedWorktreeRemovalOutcome.CloseFailed -> {
                LOG.warn("Could not close the IDE window of PR #$prNumber, so its work tree was not removed")
                notify(
                    contentKey = "workspace.watchedProjects.worktree.remove.closeFailed",
                    contentArgs = arrayOf<Any>(prNumber),
                    type = NotificationType.WARNING
                )
            }
            is WatchedWorktreeRemovalOutcome.RemovalFailed -> {
                LOG.warn("Could not remove the work tree of PR #$prNumber: ${outcome.output}", outcome.cause)
                notify(
                    contentKey = "workspace.watchedProjects.worktree.open.removeFailed",
                    contentArgs = arrayOf<Any>(prNumber),
                    type = NotificationType.ERROR
                )
            }
        }
        onFinished()
    }

    private fun notify(contentKey: String, contentArgs: Array<Any>, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                title = "",
                content = BbCloudBundle.message(contentKey, *contentArgs),
                type = type
            ).notify(project)
    }

    companion object {
        private val LOG = logger<PullRequestWorktreeRemovalLauncher>()
        private const val NOTIFICATION_GROUP_ID = "BbCloud"

        /** Work tree paths with a removal in progress. Application wide, so two tabs or windows cannot remove the same work tree twice. */
        private val worktreesBeingRemoved: MutableSet<Path> = ConcurrentHashMap.newKeySet()
    }
}