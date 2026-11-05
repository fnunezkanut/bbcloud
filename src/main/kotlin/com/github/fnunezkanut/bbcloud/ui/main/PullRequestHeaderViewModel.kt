package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.ItemCount
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.github.fnunezkanut.bbcloud.git.BranchDiffLineTotals
import com.github.fnunezkanut.bbcloud.git.PrBranchRole

/**
 * Immutable state for the Jewel pull-request detail header. A null [commitsBehind] hides the corresponding affordance,
 * mirroring the previous Swing panel's visibility toggles.
 *
 * The author, title, and whole-pull-request meta (created/updated) formerly carried here are now rendered by the
 * detail info card ([PullRequestInfoCard]); whole-pull-request line totals live in the work tree banner above the
 * editor (see `WorktreePrViewBlueBar`). This state therefore carries only the commits-behind affordance.
 */
data class PullRequestHeaderUiState(val commitsBehind: HeaderCommitsBehind? = null, val localBehindRemote: HeaderLocalBehindRemote? = null)

data class HeaderCommitsBehind(val text: String)

/** One line of the red warning: the branch it is about and the already-formatted text. */
data class LocalBranchBehindLine(val role: PrBranchRole, val text: String)

/**
 * The red "local PR branches are behind Bitbucket" warning state.
 *
 * A non-null value shows the warning; [updateInProgress] disables the update link while an update or a page reload
 * runs. All texts are already formatted: a [title], one line per behind branch, an [explanation], and the
 * [updateLinkText] of the single link that updates every branch that is behind.
 */
data class HeaderLocalBehindRemote(
    val title: String,
    val lines: List<LocalBranchBehindLine>,
    val explanation: String,
    val updateLinkText: String,
    val updateInProgress: Boolean = false
)

data class HeaderLinesChanged(val emoji: String, val addedText: String, val removedText: String, val tooltip: String) {
    companion object {
        /**
         * Builds the emoji, the "+N"/"-N" texts, and the tooltip from the git [totals]. A pure mapping free of any UI
         * state so the banner and any other caller can share it; [BranchDiffLineTotals.hasBinaryFiles] switches to the
         * approximate messages that mark the counts as a lower bound.
         */
        fun fromTotals(totals: BranchDiffLineTotals): HeaderLinesChanged {
            val emoji = DiffstatLinesEmoji.forLinesAdded(totals.linesAdded)
            return if (totals.hasBinaryFiles) {
                HeaderLinesChanged(
                    emoji = emoji,
                    addedText = BbCloudBundle.message("main.branch.header.linesAdded.approx", totals.linesAdded),
                    removedText = BbCloudBundle.message("main.branch.header.linesRemoved.approx", totals.linesRemoved),
                    tooltip = BbCloudBundle.message(
                        "main.branch.header.linesChanged.tooltip.approx",
                        totals.linesAdded,
                        totals.linesRemoved
                    )
                )
            } else {
                HeaderLinesChanged(
                    emoji = emoji,
                    addedText = BbCloudBundle.message("main.branch.header.linesAdded", totals.linesAdded),
                    removedText = BbCloudBundle.message("main.branch.header.linesRemoved", totals.linesRemoved),
                    tooltip = BbCloudBundle.message(
                        "main.branch.header.linesChanged.tooltip",
                        totals.linesAdded,
                        totals.linesRemoved
                    )
                )
            }
        }
    }
}

/** Holds the commits-behind state for the pull-request detail view. */
class PullRequestHeaderViewModel {
    val state: MutableState<PullRequestHeaderUiState> = mutableStateOf(PullRequestHeaderUiState())

    fun showCommitsBehind(state: PullRequestState, commitsBehind: ItemCount?, destinationBranch: String) {
        if (state != PullRequestState.Open || commitsBehind == null || commitsBehind.value == 0) {
            clearCommitsBehind()
            return
        }
        val countText = if (commitsBehind.isTruncated) {
            BbCloudBundle.message("main.branch.count.truncated", commitsBehind.value)
        } else {
            commitsBehind.value.toString()
        }
        val text = BbCloudBundle.message(
            "main.branch.overview.behind",
            countText,
            commitsBehind.value,
            destinationBranch
        )
        this.state.value = this.state.value.copy(commitsBehind = HeaderCommitsBehind(text = text))
    }

    fun clearCommitsBehind() {
        if (state.value.commitsBehind != null) {
            state.value = state.value.copy(commitsBehind = null)
        }
    }

    /**
     * Shows the red local-behind-remote warning. Showing a fresh warning resets the update-in-progress flag so a new
     * check always re-enables the link; an equal state is not written again, which avoids recomposition churn from
     * the background poll.
     */
    fun showLocalBehindRemote(warning: HeaderLocalBehindRemote) {
        val fresh = warning.copy(updateInProgress = false)
        if (state.value.localBehindRemote == fresh) {
            return
        }
        state.value = state.value.copy(localBehindRemote = fresh)
    }

    fun clearLocalBehindRemote() {
        if (state.value.localBehindRemote != null) {
            state.value = state.value.copy(localBehindRemote = null)
        }
    }

    /** Toggles the update link's disabled state while a pull or a page reload runs (decision 7a). */
    fun setLocalBehindRemoteUpdateInProgress(inProgress: Boolean) {
        val current = state.value.localBehindRemote ?: return
        if (current.updateInProgress == inProgress) {
            return
        }
        state.value = state.value.copy(localBehindRemote = current.copy(updateInProgress = inProgress))
    }
}