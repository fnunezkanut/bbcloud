package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.ui.graphics.Color
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestAction
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState
import com.intellij.ui.JBColor
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * Decides which pull-request actions are offered, how they are labelled, which icon they carry, and which colour their
 * outline uses.
 */
object PullRequestActionVisibility {
    /** The outline colour of the actions that do not lead to a particular pull-request state. */
    private fun neutralColor(): Color {
        return JBColor.GRAY.toComposeColor()
    }

    /** Returns the header actions in display order; closed pull requests have none. */
    fun visibleActions(state: PullRequestState, isDraft: Boolean, isApprovedByCurrentUser: Boolean): List<PullRequestAction> {
        if (state != PullRequestState.Open) {
            return emptyList()
        }
        return buildList {
            if (!isDraft) {
                // Merge remains visible; merge-check gating belongs in PullRequestDetailViewModel.recomputeActions.
                add(PullRequestAction.Merge)
                // Review-decision actions are hidden entirely while a pull request is a draft; they only appear once
                // the pull request is marked ready for review.
                add(if (isApprovedByCurrentUser) PullRequestAction.Unapprove else PullRequestAction.Approve)
            }
            add(if (isDraft) PullRequestAction.MarkAsReady else PullRequestAction.MarkAsDraft)
            add(PullRequestAction.Decline)
        }
    }

    fun label(action: PullRequestAction): String {
        return when (action) {
            PullRequestAction.Merge -> BbCloudBundle.message("main.branch.action.merge")
            PullRequestAction.Approve -> BbCloudBundle.message("main.branch.action.approve")
            PullRequestAction.Unapprove -> BbCloudBundle.message("main.branch.action.unapprove")
            PullRequestAction.MarkAsDraft -> BbCloudBundle.message("main.branch.action.markDraft")
            PullRequestAction.MarkAsReady -> BbCloudBundle.message("main.branch.action.markReady")
            PullRequestAction.Decline -> BbCloudBundle.message("main.branch.action.decline")
        }
    }

    /** The icon of an action button. */
    fun iconKey(action: PullRequestAction): IconKey {
        return when (action) {
            PullRequestAction.Merge -> AllIconsKeys.Vcs.Merge
            PullRequestAction.Approve -> AllIconsKeys.Actions.Checked
            PullRequestAction.Unapprove -> AllIconsKeys.Actions.Cancel
            PullRequestAction.MarkAsDraft -> AllIconsKeys.Actions.Edit
            PullRequestAction.MarkAsReady -> AllIconsKeys.Actions.Commit
            PullRequestAction.Decline -> AllIconsKeys.Actions.Close
        }
    }

    /** The colour of the outline of an action button. */
    fun outlineColor(action: PullRequestAction): Color {
        return when (action) {
            PullRequestAction.Merge -> PullRequestStateStyle.color(PullRequestState.Open)
            PullRequestAction.Approve -> PullRequestStateStyle.color(PullRequestState.Merged)
            PullRequestAction.Unapprove -> PullRequestStateStyle.draftColor()
            PullRequestAction.MarkAsDraft, PullRequestAction.MarkAsReady -> neutralColor()
            PullRequestAction.Decline -> PullRequestStateStyle.color(PullRequestState.Declined)
        }
    }
}