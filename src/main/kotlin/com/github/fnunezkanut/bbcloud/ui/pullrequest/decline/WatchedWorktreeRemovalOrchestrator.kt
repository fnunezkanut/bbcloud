package com.github.fnunezkanut.bbcloud.ui.pullrequest.decline

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.project.WatchedWorktreeRemovalOutcome
import com.github.fnunezkanut.bbcloud.project.WatchedWorktreeRemover
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the removal of a Watched Projects work tree on [scope] and shows a progress modal for at most
 * [modalAutoCloseTimeout]. The removal is a separate job, so the modal closing (by timeout or by dismissal) never stops it.
 * The removal closes an IDE window open on the work tree first, which can only happen once the modal has released the EDT.
 */
internal class WatchedWorktreeRemovalOrchestrator(
    private val scope: CoroutineScope,
    private val remover: WatchedWorktreeRemover,
    private val modal: CleanupModal = WatchedWorktreeProgressModal,
    private val modalAutoCloseTimeout: Duration = MODAL_AUTO_CLOSE_TIMEOUT
) {
    /**
     * Starts the removal and the modal, then returns at once. [onCompleted] runs on [scope] with the terminal outcome,
     * including after an unexpected failure, and it runs even when the modal has already closed.
     */
    fun launch(project: Project, sourceCheckoutRoot: Path, worktreePath: Path, workTreeDirectory: Path, onCompleted: (WatchedWorktreeRemovalOutcome) -> Unit) {
        val removalJob = scope.async {
            remove(
                sourceCheckoutRoot = sourceCheckoutRoot,
                worktreePath = worktreePath,
                workTreeDirectory = workTreeDirectory
            )
        }
        scope.launch {
            onCompleted(removalJob.await())
        }
        scope.launch {
            try {
                modal.show(project = project) {
                    val settled = withTimeoutOrNull(modalAutoCloseTimeout) {
                        removalJob.join()
                    }
                    if (settled == null) {
                        LOG.info(
                            "Watched work tree removal of ${worktreePath.fileName} modal auto-closed after " +
                                "$modalAutoCloseTimeout while work continues"
                        )
                        logIfWindowStillOpen(project = project, worktreePath = worktreePath)
                    }
                }
            } catch (exception: CancellationException) {
                LOG.debug("Watched work tree removal of ${worktreePath.fileName} modal was dismissed while work continues")
            } catch (exception: Exception) {
                LOG.warn("Watched work tree removal of ${worktreePath.fileName} modal failed", exception)
            }
        }
    }

    private suspend fun remove(sourceCheckoutRoot: Path, worktreePath: Path, workTreeDirectory: Path): WatchedWorktreeRemovalOutcome {
        return try {
            remover.remove(
                sourceCheckoutRoot = sourceCheckoutRoot,
                worktreePath = worktreePath,
                workTreeDirectory = workTreeDirectory
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LOG.debug("Watched work tree removal of ${worktreePath.fileName} failed unexpectedly", exception)
            WatchedWorktreeRemovalOutcome.RemovalFailed(output = exception.message.orEmpty(), cause = exception)
        }
    }

    /**
     * Logs when the IDE window showing [worktreePath] is still open after the modal closed. The close is already queued
     * inside the remover, so this only records that the work is still in progress.
     */
    private fun logIfWindowStillOpen(project: Project, worktreePath: Path) {
        if (project.isDisposed || !project.isOpen) {
            return
        }
        val basePath = project.basePath ?: return
        val projectPath = runCatching { Path.of(basePath).toAbsolutePath().normalize() }.getOrNull() ?: return
        if (projectPath == worktreePath.toAbsolutePath().normalize()) {
            LOG.info("The IDE window of the work tree ${worktreePath.fileName} is still open after the modal closed")
        }
    }

    companion object {
        private val LOG = logger<WatchedWorktreeRemovalOrchestrator>()

        /** How long the removal modal stays visible. It bounds only the modal; the removal keeps running after it closes. */
        internal val MODAL_AUTO_CLOSE_TIMEOUT: Duration = 5.seconds
    }
}

/** Production [CleanupModal] for the Watched Projects removal. It is non-cancellable, so the progress cannot be dismissed early. */
internal object WatchedWorktreeProgressModal : CleanupModal {
    override suspend fun show(project: Project, block: suspend () -> Unit) {
        withModalProgress(
            owner = ModalTaskOwner.project(project),
            title = BbCloudBundle.message("workspace.watchedProjects.worktree.remove.progress"),
            cancellation = TaskCancellation.nonCancellable()
        ) {
            block()
        }
    }
}