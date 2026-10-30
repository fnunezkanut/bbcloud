@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.main.MainPanelStatusSupport
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestListingSource
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestsUiState
import org.jetbrains.jewel.ui.component.Text

/**
 * The footer notes of the merged Workspace - PRs tab, in the warning color: a "still loading more" note while one
 * source is still in flight, a per-source failure note for the authored and assigned sources, the per-source truncated
 * notes carried over from the former My PRs and Assigned PRs tabs, and the review-queue scope note once the assigned
 * source has loaded. The whole footer is skipped when there is nothing to say.
 */
@Composable
internal fun WorkspacePullRequestsFooter(state: PullRequestsUiState) {
    val assignedLoaded = PullRequestListingSource.WorkspaceAssigned in state.loadedSources
    val authoredFailed = PullRequestListingSource.WorkspaceAuthored in state.failedSources
    val assignedFailed = PullRequestListingSource.WorkspaceAssigned in state.failedSources
    val authoredTruncated = PullRequestListingSource.WorkspaceAuthored in state.truncatedSources
    val assignedTruncated = PullRequestListingSource.WorkspaceAssigned in state.truncatedSources
    val hasNote = state.isAnySourceLoading ||
        authoredFailed ||
        assignedFailed ||
        authoredTruncated ||
        assignedTruncated ||
        assignedLoaded
    if (!hasNote) {
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.isAnySourceLoading) {
            WarningLine(messageKey = "pullRequests.stillLoading")
        }
        if (authoredFailed) {
            WarningLine(messageKey = "pullRequests.sourceFailed.authored")
        }
        if (assignedFailed) {
            WarningLine(messageKey = "pullRequests.sourceFailed.assigned")
        }
        if (authoredTruncated) {
            WarningLine(messageKey = "otherProjects.openedByMe.truncated")
        }
        if (assignedTruncated) {
            WarningLine(messageKey = "otherProjects.attention.truncated")
        }
        if (assignedLoaded) {
            WarningLine(messageKey = "otherProjects.attention.scopeNote")
        }
    }
}

@Composable
private fun WarningLine(messageKey: String) {
    Text(
        text = BbCloudBundle.message(messageKey),
        color = MainPanelStatusSupport.warningForeground()
    )
}