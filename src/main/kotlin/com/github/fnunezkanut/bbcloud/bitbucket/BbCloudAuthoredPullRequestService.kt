package com.github.fnunezkanut.bbcloud.bitbucket

import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import com.intellij.openapi.diagnostic.logger

/**
 * Collects the "PRs opened by me" listing of the Other Projects tab: every open pull request the current user
 * authored in the workspace, minus [excludedRepoSlug], which is the repository of the current project and is
 * therefore already listed by the Current Project tab.
 *
 * Bitbucket answers this in one documented request, `GET /2.0/workspaces/{workspace}/pullrequests/{selected_user}`,
 * so the workspace repositories are not walked the way the review queue has to be. That route returns no
 * repository per row, so the repository behind each pull request is recovered from its own links; see
 * [BitbucketWorkspacePullRequestResponseParser].
 */
class BbCloudAuthoredPullRequestService(private val credentialStore: BbCloudCredentialStore, private val pullRequestClient: BitbucketPullRequestClient) {
    suspend fun listAuthoredPullRequests(organizationUrl: String, excludedRepoSlug: String?): AuthoredPullRequestListResult {
        val workspace = BitbucketRepositoryUrls.workspaceFromOrganizationUrl(organizationUrl)
        if (workspace == null) {
            LOG.info("Cannot list the pull requests you opened: no workspace in organization URL $organizationUrl")
            return AuthoredPullRequestListResult.Failed()
        }

        val apiToken = credentialStore.getToken()
        if (apiToken == null) {
            LOG.info("Cannot list the pull requests you opened: no stored API token")
            return AuthoredPullRequestListResult.Failed()
        }

        // The route is keyed by uuid, which the nickname-based one does not expose.
        val currentUserUuid = pullRequestClient.getCurrentUser(apiToken = apiToken)?.uuid
        if (currentUserUuid == null) {
            LOG.info("Cannot list the pull requests you opened: the current Bitbucket user uuid is unknown")
            return AuthoredPullRequestListResult.Failed()
        }

        val page = pullRequestClient.listOpenAuthoredPullRequestsInWorkspace(
            workspace = workspace,
            selectedUserUuid = currentUserUuid,
            apiToken = apiToken
        )
        if (page == null) {
            LOG.debug("Could not list the pull requests opened in $workspace")
            return AuthoredPullRequestListResult.Failed()
        }
        if (page.isTruncated) {
            LOG.info("The pull requests opened in $workspace ran past the page limit; the listing may be incomplete")
        }

        // The exclusion is matched without case, as in ReviewingPullRequestAssembler: it is derived from the local
        // directory name, while Bitbucket slugs are lowercase, so `MyRepo` on disk has to match `myrepo` remotely.
        val excluded = excludedRepoSlug?.trim()?.lowercase()?.takeIf { slug -> slug.isNotEmpty() }
        val identified = page.entries.mapNotNull { entry ->
            val repoSlug = entry.repoSlug?.takeIf { slug -> slug.isNotBlank() } ?: return@mapNotNull null
            ReviewingPullRequest(summary = entry.summary, workspace = workspace, repoSlug = repoSlug)
        }
        val droppedCount = page.entries.size - identified.size
        if (droppedCount > 0) {
            LOG.debug("Dropped $droppedCount of ${page.entries.size} pull requests of $workspace that named no repository")
        }
        val pullRequests = identified.filter { pullRequest ->
            excluded == null || pullRequest.repoSlug.lowercase() != excluded
        }
        LOG.debug("Listed ${pullRequests.size} pull requests opened in $workspace")
        return AuthoredPullRequestListResult.Success(
            pullRequests = pullRequests,
            isTruncated = page.isTruncated || droppedCount > 0,
            // The service already resolved this uuid above, so the Mine and Approved-by-me filters make no extra request.
            currentUserUuid = currentUserUuid
        )
    }

    companion object {
        private val LOG = logger<BbCloudAuthoredPullRequestService>()
    }
}