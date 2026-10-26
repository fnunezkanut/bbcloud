package com.github.fnunezkanut.bbcloud.ui.main

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequest
import com.github.fnunezkanut.bbcloud.ui.otherprojects.WorkspacePullRequestRow
import com.github.fnunezkanut.bbcloud.ui.otherprojects.buildWorkspacePullRequestRow
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope

/** Holds the single pull-request information card shown above the detail tabs. */
class PullRequestInfoViewModel(private val avatarCache: AuthorAvatarCache = AuthorAvatarCache(avatarSize = 48)) {
    val state: MutableState<PullRequestInfoUiState> = mutableStateOf(PullRequestInfoUiState())

    private var displayedPullRequestId: Long? = null

    fun bind(
        summary: PullRequestSummary,
        repositoryWebUrl: String,
        organizationUrl: String?,
        workspace: String,
        repositorySlug: String,
        repositoryAvatarUrl: String?,
        coroutineScope: CoroutineScope
    ) {
        displayedPullRequestId = summary.id
        val resolvedWorkspace = workspace.ifBlank {
            organizationUrl?.let { url -> BitbucketRepositoryUrls.workspaceFromOrganizationUrl(url) }.orEmpty()
        }
        val resolvedRepositorySlug = repositorySlug.ifBlank {
            BitbucketRepositoryUrls.repoSlugFromPullRequestLink(summary.webUrl).orEmpty()
        }
        val explicitRepositoryUrl = repositoryWebUrl.takeIf { url ->
            url.isNotBlank() && BitbucketRepositoryUrls.repoSlugFromPullRequestLink(url) == null
        }
        val resolvedRepositoryUrl = explicitRepositoryUrl
            ?: BitbucketRepositoryUrls.repositoryWebUrlForWorkspace(
                workspace = resolvedWorkspace,
                repoSlug = resolvedRepositorySlug
            )
            ?: organizationUrl?.let { url ->
                BitbucketRepositoryUrls.repositoryWebUrl(
                    organizationUrl = url,
                    repoSlug = resolvedRepositorySlug
                )
            }
        if (explicitRepositoryUrl == null) {
            LOG.info("Using a derived repository URL for pull request #${summary.id}: available=${resolvedRepositoryUrl != null}")
        }
        val pullRequest = ReviewingPullRequest(
            summary = summary,
            workspace = resolvedWorkspace,
            repoSlug = resolvedRepositorySlug
        )
        val row = buildWorkspacePullRequestRow(
            pullRequest = pullRequest,
            organizationUrl = organizationUrl,
            authorAvatarUrl = summary.authorAvatarUrl,
            repoAvatarUrl = repositoryAvatarUrl
        ).copy(repositoryWebUrl = resolvedRepositoryUrl)
        state.value = PullRequestInfoUiState(
            hasContent = true,
            row = row,
            avatarRevision = state.value.avatarRevision
        )
        prefetchAvatars(
            summary = summary,
            repositoryAvatarUrl = repositoryAvatarUrl,
            coroutineScope = coroutineScope
        )
    }

    fun reset() {
        displayedPullRequestId = null
        state.value = PullRequestInfoUiState()
    }

    fun avatarIconFor(url: String?, fallback: javax.swing.Icon): javax.swing.Icon {
        return avatarCache.iconFor(url = url, fallback = fallback)
    }

    private fun prefetchAvatars(summary: PullRequestSummary, repositoryAvatarUrl: String?, coroutineScope: CoroutineScope) {
        avatarCache.prefetch(
            urls = listOfNotNull(summary.authorAvatarUrl, repositoryAvatarUrl),
            coroutineScope = coroutineScope,
            onLoaded = {
                if (displayedPullRequestId == summary.id) {
                    bumpAvatarRevision()
                }
            }
        )
    }

    private fun bumpAvatarRevision() {
        state.value = state.value.copy(avatarRevision = state.value.avatarRevision + 1)
    }

    companion object {
        private val LOG = logger<PullRequestInfoViewModel>()
    }
}

data class PullRequestInfoUiState(val hasContent: Boolean = false, val row: WorkspacePullRequestRow? = null, val avatarRevision: Int = 0)