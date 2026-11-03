@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.main.MainPanelStatusSupport
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestListingSource
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestsUiState
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text

/** Log category for the top-level composables of this file; `thisLogger()` would bind to the wrong Compose receiver. */
private object WorkspacePullRequestsFooterLogCategory

private val LOG = logger<WorkspacePullRequestsFooterLogCategory>()

/**
 * The footer notes of the merged Workspace tab, in the warning color: a "still loading more" note while one
 * source is still in flight, a per-source failure note for the authored and assigned sources, and the per-source truncated
 * notes carried over from the former My PRs and Assigned PRs tabs. Once the assigned source has loaded, the scope note is
 * a link that opens the Watched Projects tab. The whole footer is skipped when there is nothing to say.
 */
@Composable
internal fun WorkspacePullRequestsFooter(state: PullRequestsUiState, onGoToWatchedProjects: () -> Unit) {
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
            ScopeNoteLink(onGoToWatchedProjects = onGoToWatchedProjects)
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

/** The scope note line: plain text followed by a "Watched Projects" link that opens the Watched Projects tab. */
@Composable
private fun ScopeNoteLink(onGoToWatchedProjects: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = BbCloudBundle.message("otherProjects.attention.scopeNote.prefix"),
            color = MainPanelStatusSupport.warningForeground()
        )
        Link(
            text = BbCloudBundle.message("otherProjects.attention.scopeNote.watchedProjectsLink"),
            onClick = {
                LOG.info("User opened the Watched Projects tab from the Workspace scope note")
                onGoToWatchedProjects()
            }
        )
    }
}