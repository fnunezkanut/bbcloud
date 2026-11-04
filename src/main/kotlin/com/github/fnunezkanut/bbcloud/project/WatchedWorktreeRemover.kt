package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.git.OpenProjectLocator
import com.github.fnunezkanut.bbcloud.git.WorktreeRemovalResult
import com.github.fnunezkanut.bbcloud.ui.pullrequest.decline.ProjectCloser
import java.nio.file.Path

/**
 * Removes a PR work tree listed in Watched Projects. An IDE window open on the work tree is closed first, because git
 * cannot remove a folder that a live project still uses. The removal itself, including the prune, is delegated to
 * [removeWorktree].
 */
internal class WatchedWorktreeRemover(
    private val openProjectLocator: OpenProjectLocator,
    private val projectCloser: ProjectCloser,
    private val removeWorktree: suspend (sourceCheckoutRoot: Path, worktreePath: Path, workTreeDirectory: Path) -> WorktreeRemovalResult
) {
    suspend fun remove(sourceCheckoutRoot: Path, worktreePath: Path, workTreeDirectory: Path): WatchedWorktreeRemovalOutcome {
        val openProject = openProjectLocator.findOpenProjectForPath(path = worktreePath)
        if (openProject != null && !projectCloser.close(project = openProject)) {
            return WatchedWorktreeRemovalOutcome.CloseFailed
        }
        return when (
            val result = removeWorktree(sourceCheckoutRoot, worktreePath, workTreeDirectory)
        ) {
            WorktreeRemovalResult.Success -> WatchedWorktreeRemovalOutcome.Removed
            is WorktreeRemovalResult.Failed -> WatchedWorktreeRemovalOutcome.RemovalFailed(
                output = result.output,
                cause = result.cause
            )
        }
    }
}

/** The terminal result of removing one Watched Projects work tree. */
internal sealed interface WatchedWorktreeRemovalOutcome {
    /** The IDE window (if any) was closed and the work tree was removed and pruned. */
    data object Removed : WatchedWorktreeRemovalOutcome

    /** The IDE window on the work tree could not be closed, so the work tree was left in place. */
    data object CloseFailed : WatchedWorktreeRemovalOutcome

    /** Closing succeeded, but git could not remove the work tree. [output] is git's text and [cause] any throwable. */
    data class RemovalFailed(val output: String, val cause: Throwable?) : WatchedWorktreeRemovalOutcome
}