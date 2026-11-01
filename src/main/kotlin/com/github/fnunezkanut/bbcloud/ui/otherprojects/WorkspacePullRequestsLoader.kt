package com.github.fnunezkanut.bbcloud.ui.otherprojects

import com.github.fnunezkanut.bbcloud.bitbucket.AuthoredPullRequestListResult
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.bitbucket.ReviewingPullRequestListResult
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestListEntry
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestListingSource
import com.github.fnunezkanut.bbcloud.ui.pullrequests.PullRequestsViewModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The stored configuration and the Bitbucket listings the Workspace tab reads, backed by the configuration,
 * authored-PR, and review-queue services in production and by a fake in tests.
 */
interface WorkspacePullRequestsDataSource {
    fun organizationUrlOrNull(): String?

    fun projectsDirectoryOrNull(): String?

    fun watchedRepoSlugs(): List<String>

    fun repoAvatarUrls(): Map<String, String>

    suspend fun listAuthored(organizationUrl: String, excludedRepoSlug: String?): AuthoredPullRequestListResult

    suspend fun listReviewing(organizationUrl: String, excludedRepoSlug: String?, repoSlugs: List<String>): ReviewingPullRequestListResult
}

/**
 * Loads the two sources of the merged Workspace tab (pull requests the user opened and pull requests the user
 * reviews) into [pullRequestsViewModel] and keeps the [headerViewModel] refresh button disabled while any load runs.
 * The host only switches its card through [onShowContentCallback] and [onShowNotConfiguredCallback].
 */
class WorkspacePullRequestsLoader(
    private val pullRequestsViewModel: PullRequestsViewModel,
    private val headerViewModel: WorkspaceHeaderViewModel,
    private val dataSource: WorkspacePullRequestsDataSource,
    private val onShowContentCallback: () -> Unit,
    private val onShowNotConfiguredCallback: () -> Unit,
    private val runOnEdt: (() -> Unit) -> Unit = { block -> ApplicationManager.getApplication().invokeLater(block) }
) {
    /** Counter so overlapping loads keep the refresh button disabled until all finish. */
    private var activeLoadCount = 0

    /**
     * Loads both Workspace sources under one refresh generation, so a single Refresh starts one generation and a
     * slow response of one source cannot overwrite a newer load of the other.
     */
    fun loadWorkspacePullRequests(excludedRepoSlug: String?, coroutineScope: CoroutineScope) {
        val generation = pullRequestsViewModel.beginRefresh()
        loadAuthored(excludedRepoSlug = excludedRepoSlug, generation = generation, coroutineScope = coroutineScope)
        loadReviewing(excludedRepoSlug = excludedRepoSlug, generation = generation, coroutineScope = coroutineScope)
    }

    fun showNotConfigured() {
        headerViewModel.setRefreshVisible(false)
        headerViewModel.setRefreshEnabled(false)
        pullRequestsViewModel.showHidden()
        onShowNotConfiguredCallback()
    }

    private fun loadAuthored(excludedRepoSlug: String?, generation: Long, coroutineScope: CoroutineScope) {
        val organizationUrl = dataSource.organizationUrlOrNull()
        if (organizationUrl == null) {
            LOG.info("Cannot load authored PRs: no organization URL")
            pullRequestsViewModel.failSource(generation = generation, source = PullRequestListingSource.WorkspaceAuthored)
            return
        }
        val workspaceOverviewUrl = BitbucketRepositoryUrls.workspaceOverviewUrl(organizationUrl)
        val workspaceName = BitbucketRepositoryUrls.workspaceFromOrganizationUrl(organizationUrl)
        headerViewModel.setWorkspaceUrl(url = workspaceOverviewUrl, name = workspaceName)
        headerViewModel.setRefreshVisible(true)
        onShowContentCallback()
        beginLoad()
        coroutineScope.launch {
            val result = dataSource.listAuthored(
                organizationUrl = organizationUrl,
                excludedRepoSlug = excludedRepoSlug
            )
            runOnEdt {
                showAuthoredResult(result = result, generation = generation, coroutineScope = coroutineScope)
                endLoad()
            }
        }
    }

    private fun loadReviewing(excludedRepoSlug: String?, generation: Long, coroutineScope: CoroutineScope) {
        val organizationUrl = dataSource.organizationUrlOrNull()
        val projectsDirectory = dataSource.projectsDirectoryOrNull()
        val workspaceOverviewUrl = organizationUrl?.let { BitbucketRepositoryUrls.workspaceOverviewUrl(it) }
        val workspaceName = organizationUrl?.let { BitbucketRepositoryUrls.workspaceFromOrganizationUrl(it) }
        headerViewModel.setWorkspaceUrl(url = workspaceOverviewUrl, name = workspaceName)
        headerViewModel.setRefreshVisible(workspaceOverviewUrl != null)
        if (organizationUrl == null) {
            LOG.info("Cannot load reviewing PRs: no organization URL")
            pullRequestsViewModel.failSource(generation = generation, source = PullRequestListingSource.WorkspaceAssigned)
            return
        }
        if (projectsDirectory == null) {
            LOG.info("Assigned PRs: projects directory not set; prompting for Settings")
            // Route to the generic centered not-configured card (R2) rather than showing partial content with the
            // header still visible. showNotConfigured switches the single Workspace root and hides its refresh
            // button.
            showNotConfigured()
            return
        }
        val repoSlugs = dataSource.watchedRepoSlugs()
        if (repoSlugs.isEmpty()) {
            LOG.info("No watched projects are enabled for the current workspace, so no pull requests to review are listed")
        }
        headerViewModel.setRefreshVisible(true)
        onShowContentCallback()
        beginLoad()
        coroutineScope.launch {
            val result = dataSource.listReviewing(
                organizationUrl = organizationUrl,
                excludedRepoSlug = excludedRepoSlug,
                repoSlugs = repoSlugs
            )
            runOnEdt {
                showReviewingResult(result = result, generation = generation, coroutineScope = coroutineScope)
                endLoad()
            }
        }
    }

    private fun showAuthoredResult(result: AuthoredPullRequestListResult, generation: Long, coroutineScope: CoroutineScope) {
        when (result) {
            is AuthoredPullRequestListResult.Success -> {
                // The repository avatar lookup is copied verbatim from WorkspaceMyPullRequestsViewModel.showResult: the
                // persisted URL map is keyed by the raw repoSlug, with no lowercasing.
                val avatarUrlBySlug = dataSource.repoAvatarUrls()
                val entries = result.pullRequests.map { pullRequest ->
                    PullRequestListEntry(
                        pullRequest = pullRequest,
                        source = PullRequestListingSource.WorkspaceAuthored,
                        repoAvatarUrl = avatarUrlBySlug[pullRequest.repoSlug]
                    )
                }
                pullRequestsViewModel.applySource(
                    generation = generation,
                    source = PullRequestListingSource.WorkspaceAuthored,
                    entries = entries,
                    isTruncated = result.isTruncated,
                    currentUserUuid = result.currentUserUuid,
                    coroutineScope = coroutineScope
                )
            }
            is AuthoredPullRequestListResult.Failed -> {
                if (result.cause != null) {
                    LOG.warn("Could not load the pull requests you opened", result.cause)
                } else {
                    LOG.warn("Could not load the pull requests you opened")
                }
                pullRequestsViewModel.failSource(generation = generation, source = PullRequestListingSource.WorkspaceAuthored)
            }
        }
    }

    private fun showReviewingResult(result: ReviewingPullRequestListResult, generation: Long, coroutineScope: CoroutineScope) {
        when (result) {
            is ReviewingPullRequestListResult.Success -> {
                // The repository avatar lookup is copied verbatim from WorkspaceAssignedPullRequestsViewModel.showResult:
                // the persisted URL map is keyed by the raw repoSlug, with no lowercasing.
                val avatarUrlBySlug = dataSource.repoAvatarUrls()
                val entries = result.pullRequests.map { pullRequest ->
                    PullRequestListEntry(
                        pullRequest = pullRequest,
                        source = PullRequestListingSource.WorkspaceAssigned,
                        repoAvatarUrl = avatarUrlBySlug[pullRequest.repoSlug]
                    )
                }
                pullRequestsViewModel.applySource(
                    generation = generation,
                    source = PullRequestListingSource.WorkspaceAssigned,
                    entries = entries,
                    isTruncated = result.isTruncated,
                    currentUserUuid = result.currentUserUuid,
                    coroutineScope = coroutineScope
                )
            }
            is ReviewingPullRequestListResult.Failed -> {
                if (result.cause != null) {
                    LOG.warn("Could not load the pull requests to review", result.cause)
                } else {
                    LOG.warn("Could not load the pull requests to review")
                }
                pullRequestsViewModel.failSource(generation = generation, source = PullRequestListingSource.WorkspaceAssigned)
            }
        }
    }

    private fun beginLoad() {
        activeLoadCount++
        headerViewModel.setRefreshEnabled(false)
    }

    @Synchronized
    private fun endLoad() {
        if (--activeLoadCount <= 0) {
            activeLoadCount = 0
            headerViewModel.setRefreshEnabled(true)
        }
    }

    companion object {
        private val LOG = logger<WorkspacePullRequestsLoader>()
    }
}