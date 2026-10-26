package com.github.fnunezkanut.bbcloud.ui.otherprojects

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.bitbucket.AuthoredPullRequestListResult
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import com.github.fnunezkanut.bbcloud.ui.main.AuthorAvatarCache
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope

/**
 * Holds the Workspace - My PRs state while the coordinator remains responsible for loading and navigation.
 * The Compose screen consumes this state instead of owning Swing table components.
 */
class WorkspaceMyPullRequestsViewModel(
    private val onOpenLocalProjectCallback: (ReviewingPullRequest) -> Unit,
    private val onOpenPrOnWebCallback: (ReviewingPullRequest) -> Unit,
    private val onOpenRepositoryCallback: (ReviewingPullRequest) -> Unit
) : WorkspacePullRequestsViewModelSupport {
    override val state: MutableState<WorkspacePullRequestsUiState> = mutableStateOf(WorkspacePullRequestsUiState())

    private val avatarCache = AuthorAvatarCache(avatarSize = 48)
    private val slugToAvatarUrl = mutableMapOf<String, String>()

    fun showLoading() {
        showCard(WorkspacePullRequestsCard.Loading)
        state.value = state.value.copy(rows = emptyList(), isTruncated = false, scopeNoteVisible = false)
    }

    /**
     * Fills the listing and starts public-CDN avatar prefetches. The completeness note follows [result] so a capped or
     * partly unreadable load is visible instead of looking like the whole set of pull requests the user opened.
     */
    fun showResult(
        result: AuthoredPullRequestListResult.Success,
        organizationUrl: String?,
        coroutineScope: CoroutineScope,
        avatarUrlBySlug: Map<String, String> = emptyMap()
    ) {
        slugToAvatarUrl.clear()
        slugToAvatarUrl.putAll(avatarUrlBySlug)
        val rows = result.pullRequests.map { pullRequest ->
            buildWorkspacePullRequestRow(
                pullRequest = pullRequest,
                organizationUrl = organizationUrl,
                authorAvatarUrl = pullRequest.summary.authorAvatarUrl,
                repoAvatarUrl = slugToAvatarUrl[pullRequest.repoSlug]
            )
        }
        state.value = state.value.copy(
            card = if (rows.isEmpty()) WorkspacePullRequestsCard.Empty else WorkspacePullRequestsCard.Listing,
            rows = rows,
            isTruncated = result.isTruncated,
            scopeNoteVisible = WorkspacePullRequestsTab.MyPrs.scopeNoteVisible
        )
        if (rows.isEmpty()) {
            LOG.info("No open pull requests opened by the current user in the other projects of the workspace")
        } else {
            LOG.debug("Showing ${rows.size} pull requests opened by the current user in other projects")
        }
        avatarCache.prefetch(
            urls = rows.mapNotNull { row -> row.authorAvatarUrl },
            coroutineScope = coroutineScope,
            onLoaded = { bumpAvatarRevision() }
        )
        // Prefetch the repository icons into the same cache (deduped by URL); the immediate Compose repaint shows
        // cached or placeholder icons at once while the images load.
        LOG.debug("Binding ${avatarUrlBySlug.size} repository avatar URLs for the Workspace - My PRs table")
        avatarCache.prefetch(
            urls = avatarUrlBySlug.values.distinct(),
            coroutineScope = coroutineScope,
            onLoaded = { bumpAvatarRevision() }
        )
    }

    fun showEmpty() {
        showCard(WorkspacePullRequestsCard.Empty)
    }

    fun showError() {
        showCard(WorkspacePullRequestsCard.Error)
    }

    fun showNotConfigured() {
        showCard(WorkspacePullRequestsCard.NotConfigured)
    }

    override fun onOpenLocalProject(pullRequest: ReviewingPullRequest) {
        LOG.info("User requested to open pull request #${pullRequest.summary.id} from the Workspace - My PRs table")
        onOpenLocalProjectCallback(pullRequest)
    }

    override fun onOpenPrOnWeb(pullRequest: ReviewingPullRequest) {
        LOG.info("User requested to open pull request #${pullRequest.summary.id} in the browser from the Workspace - My PRs table")
        onOpenPrOnWebCallback(pullRequest)
    }

    override fun onOpenRepository(pullRequest: ReviewingPullRequest) {
        LOG.info("User requested to open repository ${pullRequest.repoSlug} from the Workspace - My PRs table")
        onOpenRepositoryCallback(pullRequest)
    }

    override fun avatarIconFor(url: String?, fallback: javax.swing.Icon): javax.swing.Icon {
        return avatarCache.iconFor(url = url, fallback = fallback)
    }

    private fun showCard(card: WorkspacePullRequestsCard) {
        LOG.debug("Showing the Workspace - My PRs pull request card $card")
        state.value = state.value.copy(card = card)
    }

    private fun bumpAvatarRevision() {
        state.value = state.value.copy(avatarRevision = state.value.avatarRevision + 1)
    }

    companion object {
        private val LOG = logger<WorkspaceMyPullRequestsViewModel>()
    }
}