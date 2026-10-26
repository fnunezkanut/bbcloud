package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope

/** Holds the Project - Open PRs card state while the coordinator owns loading and worktree preparation. */
class ProjectOpenPullRequestsViewModel(
    private val onOpenRequestedCallback: (PullRequestSummary) -> Unit,
    private val onOpenPrOnWebCallback: (PullRequestSummary) -> Unit,
    private val onOpenRepositoryCallback: (PullRequestSummary) -> Unit
) {
    val state: MutableState<ProjectPullRequestsUiState> = mutableStateOf(ProjectPullRequestsUiState())

    private val avatarCache = AuthorAvatarCache(avatarSize = 48)

    fun showHidden() {
        state.value = state.value.copy(
            card = ProjectPullRequestsCard.Hidden,
            rows = emptyList()
        )
    }

    fun showLoading() {
        state.value = state.value.copy(
            card = ProjectPullRequestsCard.Loading,
            rows = emptyList()
        )
    }

    fun applyOpenRows(
        openPullRequests: List<PullRequestSummary>,
        organizationUrl: String?,
        workspace: String,
        repoSlug: String,
        repoAvatarUrl: String?,
        coroutineScope: CoroutineScope
    ) {
        LOG.debug("Showing ${openPullRequests.size} open Project pull requests")
        LOG.debug("Project repository avatar URL present: ${repoAvatarUrl != null}")
        val rows = openPullRequests.map { summary ->
            buildProjectPullRequestRow(
                summary = summary,
                organizationUrl = organizationUrl,
                workspace = workspace,
                repoSlug = repoSlug,
                repoAvatarUrl = repoAvatarUrl
            )
        }
        state.value = state.value.copy(
            card = if (rows.isEmpty()) ProjectPullRequestsCard.Empty else ProjectPullRequestsCard.Listing,
            rows = rows
        )
        avatarCache.prefetch(
            urls = rows.mapNotNull { row -> row.authorAvatarUrl },
            coroutineScope = coroutineScope,
            onLoaded = { bumpAvatarRevision() }
        )
        avatarCache.prefetch(
            urls = listOfNotNull(repoAvatarUrl),
            coroutineScope = coroutineScope,
            onLoaded = { bumpAvatarRevision() }
        )
    }

    fun showEmpty() {
        state.value = state.value.copy(
            card = ProjectPullRequestsCard.Empty,
            rows = emptyList()
        )
    }

    fun showError() {
        state.value = state.value.copy(
            card = ProjectPullRequestsCard.Error,
            rows = emptyList()
        )
    }

    internal fun showCard(card: ProjectPullRequestsCard) {
        LOG.debug("Showing the Project - Open PRs card $card")
        state.value = state.value.copy(card = card)
    }

    fun onOpenRequested(summary: PullRequestSummary) {
        LOG.info("User requested to open pull request #${summary.id} in its own work tree via card")
        onOpenRequestedCallback(summary)
    }

    fun onOpenPrOnWeb(summary: PullRequestSummary) {
        LOG.info("User requested to open pull request #${summary.id} in the browser via card")
        onOpenPrOnWebCallback(summary)
    }

    fun onOpenRepository(summary: PullRequestSummary) {
        LOG.info("User requested to open the repository for pull request #${summary.id} via card")
        onOpenRepositoryCallback(summary)
    }

    fun avatarIconFor(url: String?, fallback: javax.swing.Icon): javax.swing.Icon {
        return avatarCache.iconFor(url = url, fallback = fallback)
    }

    private fun bumpAvatarRevision() {
        state.value = state.value.copy(avatarRevision = state.value.avatarRevision + 1)
    }

    companion object {
        private val LOG = logger<ProjectOpenPullRequestsViewModel>()
    }
}