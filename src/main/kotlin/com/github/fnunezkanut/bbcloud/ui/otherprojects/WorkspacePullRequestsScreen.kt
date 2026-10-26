@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.ui.main.CenteredError
import com.github.fnunezkanut.bbcloud.ui.main.CenteredMessage
import com.github.fnunezkanut.bbcloud.ui.main.MainPanelStatusSupport
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestCardActions
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestCardListing
import org.jetbrains.jewel.ui.component.Text

@Composable
fun WorkspacePullRequestsScreen(viewModel: WorkspacePullRequestsViewModelSupport, tab: WorkspacePullRequestsTab) {
    val state by viewModel.state
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp)
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state.card) {
                WorkspacePullRequestsCard.NotConfigured -> Unit
                WorkspacePullRequestsCard.Loading -> CenteredMessage(
                    message = BbCloudBundle.message(tab.loadingMessageKey)
                )

                WorkspacePullRequestsCard.Empty -> CenteredMessage(
                    message = BbCloudBundle.message(tab.emptyMessageKey)
                )

                WorkspacePullRequestsCard.Error -> CenteredError(
                    message = BbCloudBundle.message(tab.errorMessageKey)
                )

                WorkspacePullRequestsCard.Listing -> PullRequestCardListing(
                    rows = state.rows,
                    avatarRevision = state.avatarRevision,
                    showRepositoryColumn = true,
                    showStateBadge = false,
                    avatarIconFor = viewModel::avatarIconFor,
                    actions = PullRequestCardActions(
                        onOpenTitle = viewModel::onOpenLocalProject,
                        onOpenPrOnWeb = viewModel::onOpenPrOnWeb,
                        onOpenRepository = viewModel::onOpenRepository
                    )
                )
            }
        }
        if (state.card == WorkspacePullRequestsCard.Listing) {
            WorkspacePullRequestsFooter(state = state, tab = tab)
        }
    }
}

@Composable
private fun WorkspacePullRequestsFooter(state: WorkspacePullRequestsUiState, tab: WorkspacePullRequestsTab) {
    if (!state.scopeNoteVisible && !state.isTruncated) {
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.scopeNoteVisible) {
            Text(
                text = BbCloudBundle.message("otherProjects.attention.scopeNote"),
                color = MainPanelStatusSupport.warningForeground()
            )
        }
        if (state.isTruncated) {
            Text(
                text = BbCloudBundle.message(tab.truncatedMessageKey),
                color = MainPanelStatusSupport.warningForeground()
            )
        }
    }
}