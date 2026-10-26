@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(
    org.jetbrains.jewel.foundation.ExperimentalJewelApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.github.fnunezkanut.bbcloud.BbCloudBundle

@Composable
fun ProjectOpenPullRequestsScreen(viewModel: ProjectOpenPullRequestsViewModel) {
    val state by viewModel.state
    Box(modifier = Modifier.fillMaxSize()) {
        when (state.card) {
            ProjectPullRequestsCard.Hidden -> Unit
            ProjectPullRequestsCard.Loading -> CenteredMessage(
                message = BbCloudBundle.message("main.pullRequests.loading")
            )

            ProjectPullRequestsCard.Empty -> CenteredMessage(
                message = BbCloudBundle.message("main.pullRequests.noneOpen")
            )

            ProjectPullRequestsCard.Error -> CenteredError(
                message = BbCloudBundle.message("main.pullRequests.loadFailed")
            )

            ProjectPullRequestsCard.Listing -> PullRequestCardListing(
                rows = state.rows,
                avatarRevision = state.avatarRevision,
                showRepositoryColumn = true,
                showStateBadge = false,
                avatarIconFor = viewModel::avatarIconFor,
                actions = PullRequestCardActions(
                    onOpenTitle = { pullRequest -> viewModel.onOpenRequested(pullRequest.summary) },
                    onOpenPrOnWeb = { pullRequest -> viewModel.onOpenPrOnWeb(pullRequest.summary) },
                    onOpenRepository = { pullRequest -> viewModel.onOpenRepository(pullRequest.summary) }
                )
            )
        }
    }
}

@Composable
fun ProjectCompletedPullRequestsScreen(viewModel: ProjectCompletedPullRequestsViewModel) {
    val state by viewModel.state
    Box(modifier = Modifier.fillMaxSize()) {
        when (state.card) {
            ProjectPullRequestsCard.Hidden -> Unit
            ProjectPullRequestsCard.Loading -> CenteredMessage(
                message = BbCloudBundle.message("main.pullRequests.loading")
            )

            ProjectPullRequestsCard.Empty -> CenteredMessage(
                message = BbCloudBundle.message("main.pullRequests.noneClosed")
            )

            ProjectPullRequestsCard.Error -> CenteredError(
                message = BbCloudBundle.message("main.pullRequests.loadFailed")
            )

            ProjectPullRequestsCard.Listing -> PullRequestCardListing(
                rows = state.rows,
                avatarRevision = state.avatarRevision,
                showRepositoryColumn = true,
                showStateBadge = true,
                avatarIconFor = viewModel::avatarIconFor,
                actions = PullRequestCardActions(
                    onOpenTitle = { pullRequest -> viewModel.onOpenInBrowser(pullRequest.summary) },
                    onOpenPrOnWeb = { pullRequest -> viewModel.onOpenPrOnWeb(pullRequest.summary) },
                    onOpenRepository = { pullRequest -> viewModel.onOpenRepository(pullRequest.summary) }
                )
            )
        }
    }
}