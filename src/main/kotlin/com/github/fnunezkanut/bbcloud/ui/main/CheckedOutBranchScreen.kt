@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import kotlinx.coroutines.CoroutineScope

/**
 * The pull-request detail view: a single information row (avatar, title link, draft/state badges, author, updated/
 * created timestamps with an absolute-date tooltip) that hosts the action buttons (Merge, Approve / Unapprove, Mark as
 * draft / ready, Decline) on its trailing edge, followed by the commits-behind line, the local-behind-remote line, and
 * the pull-request page: the description, the Activity timeline with the comment composer, and the Builds, Checks,
 * Reviewers and Tasks cards.
 *
 * Whole-pull-request line totals are not shown here; they live in the work tree banner above the editor (see
 * `WorktreePrViewBlueBar`). There is no tab strip, and the whole view is one Compose tree.
 */
@Composable
internal fun CheckedOutBranchScreen(
    detailViewModel: PullRequestDetailViewModel,
    overviewViewModel: PullRequestOverviewViewModel,
    conversationViewModel: PullRequestConversationViewModel,
    historyViewModel: PullRequestHistoryViewModel,
    infoViewModel: PullRequestInfoViewModel,
    headerViewModel: PullRequestHeaderViewModel,
    configurationService: BbCloudConfigurationService,
    coroutineScope: CoroutineScope,
    onUpdateLocalBranch: () -> Unit
) {
    val detailState by detailViewModel.state
    Column(modifier = Modifier.fillMaxSize()) {
        PullRequestInfoTab(
            viewModel = infoViewModel,
            configurationService = configurationService,
            coroutineScope = coroutineScope,
            actions = detailState.actions,
            actionsEnabled = detailState.actionsEnabled,
            disabledActions = detailState.disabledActions,
            actionTooltips = detailState.actionTooltips,
            onAction = detailViewModel::onActionClicked
        )
        // The orange "behind destination" line, then the red "local behind remote" line below it (decision 1a).
        PullRequestCommitsBehindLine(viewModel = headerViewModel)
        PullRequestLocalBehindRemoteLine(viewModel = headerViewModel, onUpdateLocalBranch = onUpdateLocalBranch)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            PullRequestOverviewScreen(
                viewModel = overviewViewModel,
                historyViewModel = historyViewModel,
                conversationViewModel = conversationViewModel
            )
        }
    }
}