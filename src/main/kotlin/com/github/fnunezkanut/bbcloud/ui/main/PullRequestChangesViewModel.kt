package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestFileCommentCounts
import com.github.fnunezkanut.bbcloud.git.BranchDiffResult
import com.github.fnunezkanut.bbcloud.git.FileLineCount
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.changes.Change

/**
 * Drives the Jewel Changes tab. The editor session pushes data through the imperative `showLoading`/`showResult`/
 * `setCommentCounts` methods (the same contract the old Swing [PullRequestFilesChangedPanel] exposed); the composable
 * reads [state] and routes user intent back through the `on*` callbacks.
 *
 * The platform changes stay in this view model so the UI state remains a plain data model: a row of the file tree is
 * resolved back to its change through [changeFor].
 */
class PullRequestChangesViewModel(
    private val pullRequestId: () -> Long?,
    private val projectBasePath: () -> String?,
    private val onRefreshRequested: () -> Unit,
    private val onOpenChange: (Change) -> Unit
) {
    val state: MutableState<PullRequestChangesUiState> = mutableStateOf(PullRequestChangesUiState())

    private var changes: List<Change> = emptyList()
    private var commentCounts: PullRequestFileCommentCounts? = null
    private var fileLineCounts: Map<String, FileLineCount> = emptyMap()

    fun showLoading() {
        state.value = state.value.copy(card = PullRequestFileChangesCard.Loading)
        LOG.debug("Loading the changed files of pull request #${pullRequestId()}")
    }

    fun showResult(result: BranchDiffResult) {
        when (result) {
            is BranchDiffResult.Success -> {
                changes = result.changes.toList()
                fileLineCounts = result.fileLineCounts
                if (changes.isEmpty()) {
                    fileLineCounts = emptyMap()
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

    fun reset() {
        changes = emptyList()
        commentCounts = null
        fileLineCounts = emptyMap()
        state.value = PullRequestChangesUiState()
    }

    /** Returns the change behind a file tree row, or null when the row is no longer part of the current diff. */
    fun changeFor(changeId: Int): Change? {
        return changes.getOrNull(changeId)
    }

    fun onRefresh() {
        LOG.info("User clicked refresh on the pull request files changed view")
        onRefreshRequested()
    }

    fun onFileOpened(changeId: Int) {
        val change = changeFor(changeId) ?: return
        LOG.info("User opened the diff of changed file $changeId of pull request #${pullRequestId()}")
        onOpenChange(change)
    }

    fun onCopyPath(relativePath: String) {
        LOG.info("User copied the path of changed file $relativePath of pull request #${pullRequestId()}")
    }

    private fun renderRows() {
        val rows = toFileChangeRows(
            changes = changes,
            commentCounts = commentCounts,
            projectBasePath = projectBasePath(),
            fileLineCounts = fileLineCounts
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