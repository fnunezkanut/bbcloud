package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestFileCommentCounts
import com.github.fnunezkanut.bbcloud.git.BranchDiffResult
import com.github.fnunezkanut.bbcloud.git.FileLineCount
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil

/**
 * Drives the file-changes view of the PR Changes tool window. The tool window service pushes data through the imperative
 * `showLoading`/`showResult`/`setCommentCounts` methods (the same contract the old Swing [PullRequestFilesChangedPanel]
 * exposed); the composable
 * reads [state] and routes user intent back through the `on*` callbacks.
 *
 * The platform changes stay in this view model so the UI state remains a plain data model: a row of the file tree is
 * resolved back to its change through [changeFor].
 */
class PullRequestChangesViewModel(
    private val pullRequestId: () -> Long?,
    private val projectBasePath: () -> String?,
    private val onRefreshRequested: () -> Unit,
    private val onOpenChange: (Change) -> Unit,
    /**
     * Opens a diff chain of all [changes] with the clicked one at [index] selected, so the user can step through the
     * files with next/previous. When null, a single change is opened through [onOpenChange] instead.
     */
    private val onOpenChangeInList: ((changes: List<Change>, index: Int) -> Unit)? = null,
    /**
     * Tells whether a file is marked Viewed at the given source-branch revision. Called from the composable for each
     * row, so the answer must come from an observable store. The default never reports a file as viewed.
     */
    private val isFileViewed: (pullRequestId: Long, relativePath: String, revision: String?) -> Boolean = { _, _, _ -> false }
) {
    val state: MutableState<PullRequestChangesUiState> = mutableStateOf(PullRequestChangesUiState())

    private var changes: List<Change> = emptyList()
    private var commentCounts: PullRequestFileCommentCounts? = null
    private var fileLineCounts: Map<String, FileLineCount> = emptyMap()
    private var fileRevisions: Map<String, String> = emptyMap()

    fun showLoading() {
        state.value = state.value.copy(card = PullRequestFileChangesCard.Loading)
        LOG.debug("Loading the changed files of pull request #${pullRequestId()}")
    }

    fun showResult(result: BranchDiffResult) {
        when (result) {
            is BranchDiffResult.Success -> {
                changes = result.changes.toList()
                fileLineCounts = result.fileLineCounts
                fileRevisions = result.fileRevisions
                if (changes.isEmpty()) {
                    fileLineCounts = emptyMap()
                    fileRevisions = emptyMap()
                    state.value = state.value.copy(
                        card = PullRequestFileChangesCard.Empty,
                        rows = emptyList(),
                        errorMessage = null
                    )
                    LOG.debug("No changed files between the branches of pull request #${pullRequestId()}")
                } else {
                    renderRows()
                }
            }
            BranchDiffResult.NoRepository -> {
                showError(BbCloudBundle.message("main.branch.filesChanged.noRepository"))
            }
            is BranchDiffResult.BranchNotFound -> {
                showError(BbCloudBundle.message("main.branch.filesChanged.branchNotFound", result.branchName))
            }
            is BranchDiffResult.Failed -> {
                val detail = result.message.takeIf { it.isNotBlank() }
                val message = if (detail != null) {
                    BbCloudBundle.message("main.branch.filesChanged.failedWithDetail", detail)
                } else {
                    BbCloudBundle.message("main.branch.filesChanged.failed")
                }
                showError(message)
            }
        }
    }

    fun setCommentCounts(counts: PullRequestFileCommentCounts?) {
        commentCounts = counts
        LOG.debug("Applied per-file comment counts for ${counts?.pathsWithComments()?.size ?: 0} files")
        // The comment badge is part of the row, so the rows are re-mapped to pick up the new counts.
        if (state.value.card == PullRequestFileChangesCard.Listing) {
            renderRows()
        }
    }

    /**
     * Shows the `Resolved` card instead of a file tree, used by the work-tree PR Changes tool window when the pull
     * request is no longer open. The backing diff is cleared so a stale row can no longer open a diff.
     */
    fun showResolved(pullRequestId: Long?) {
        changes = emptyList()
        commentCounts = null
        fileLineCounts = emptyMap()
        fileRevisions = emptyMap()
        state.value = PullRequestChangesUiState(
            card = PullRequestFileChangesCard.Resolved,
            resolvedPullRequestId = pullRequestId
        )
        LOG.info("Not showing the changed files of pull request #$pullRequestId: the pull request is resolved")
    }

    fun reset() {
        changes = emptyList()
        commentCounts = null
        fileLineCounts = emptyMap()
        fileRevisions = emptyMap()
        state.value = PullRequestChangesUiState()
    }

    /** Returns the change behind a file tree row, or null when the row is no longer part of the current diff. */
    fun changeFor(changeId: Int): Change? {
        return changes.getOrNull(changeId)
    }

    /**
     * Returns whether [row] is marked Viewed at its current source-branch revision. Returns false when no pull request
     * is shown. Reads the viewed store, so calling this from a composable recomposes the row when the mark changes.
     */
    fun isViewed(row: PullRequestFileChangeRow): Boolean {
        val currentPullRequestId = pullRequestId() ?: return false
        return isFileViewed(currentPullRequestId, row.relativePath, row.revision)
    }

    fun onRefresh() {
        LOG.info("User clicked refresh on the pull request files changed view")
        onRefreshRequested()
    }

    fun onFileOpened(changeId: Int) {
        val change = changeFor(changeId) ?: return
        LOG.info("User opened the diff of changed file $changeId of pull request #${pullRequestId()}")
        val openInList = onOpenChangeInList
        if (openInList != null) {
            openInList(changes, changeId)
        } else {
            onOpenChange(change)
        }
    }

    fun onCopyPath(relativePath: String) {
        LOG.info("User copied the path of changed file $relativePath of pull request #${pullRequestId()}")
    }

    /**
     * Returns the absolute filesystem path of the changed file behind [changeId], or null when the row is no longer
     * part of the current diff. The path is read from the backing change, the same source the row mapping used, so it
     * is the real absolute path rather than a reconstruction.
     */
    fun absolutePathFor(changeId: Int): String? {
        val change = changeFor(changeId)
        if (change == null) {
            LOG.info("No changed file for id $changeId of pull request #${pullRequestId()} to copy the absolute path of")
            return null
        }
        val absolutePath = ChangesUtil.getFilePath(change).path
        LOG.info("User copied the absolute path of changed file $changeId of pull request #${pullRequestId()}: $absolutePath")
        return absolutePath
    }

    /**
     * Returns the absolute filesystem path of the changed-files tree directory whose project-relative path is
     * [directoryRelativePath], or null when the project base path is unknown. A directory has no backing change, so its
     * absolute path is composed from the project base path the diff was mapped against. An empty
     * [directoryRelativePath] resolves to the base path itself.
     */
    fun directoryAbsolutePathFor(directoryRelativePath: String): String? {
        val basePath = projectBasePath()
        if (basePath == null) {
            LOG.info("No project base path to copy the absolute path of directory '$directoryRelativePath' of pull request #${pullRequestId()}")
            return null
        }
        val absolutePath = if (directoryRelativePath.isEmpty()) {
            basePath
        } else {
            "$basePath/$directoryRelativePath"
        }
        LOG.info("User copied the absolute path of directory '$directoryRelativePath' of pull request #${pullRequestId()}: $absolutePath")
        return absolutePath
    }

    private fun renderRows() {
        val rows = toFileChangeRows(
            changes = changes,
            commentCounts = commentCounts,
            projectBasePath = projectBasePath(),
            fileLineCounts = fileLineCounts,
            fileRevisions = fileRevisions
        )
        state.value = state.value.copy(
            card = PullRequestFileChangesCard.Listing,
            rows = rows,
            errorMessage = null
        )
        LOG.debug("Rendered ${rows.size} changed files for pull request #${pullRequestId()}")
    }

    private fun showError(message: String) {
        fileLineCounts = emptyMap()
        fileRevisions = emptyMap()
        state.value = state.value.copy(
            card = PullRequestFileChangesCard.Error,
            rows = emptyList(),
            errorMessage = message
        )
        LOG.info("Cannot show the changed files of pull request #${pullRequestId()}: $message")
    }

    companion object {
        private val LOG = logger<PullRequestChangesViewModel>()
    }
}