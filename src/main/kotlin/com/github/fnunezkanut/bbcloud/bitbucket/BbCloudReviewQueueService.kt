package com.github.fnunezkanut.bbcloud.bitbucket

import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The review queue is built from the repositories the caller passes in, which are the verified watched projects of the
 * configured workspace; each repository slug is used as given; the current project is left out; a 404 or 403 skips a
 * repository; a 401 fails the load; failed repositories and the cap mark the result as truncated.
 */
class BbCloudReviewQueueService(private val credentialStore: BbCloudCredentialStore, private val pullRequestClient: BitbucketPullRequestClient) {
    /**
     * Lists the open pull requests the current user reviews, across [repoSlugs]. Empty [repoSlugs] yields an empty
     * success without any repository request.
     */
    suspend fun listReviewingPullRequests(organizationUrl: String, excludedRepoSlug: String?, repoSlugs: List<String>): ReviewingPullRequestListResult {
        val workspace = BitbucketRepositoryUrls.workspaceFromOrganizationUrl(organizationUrl)
        if (workspace == null) {
            LOG.info("Cannot list the pull requests to review: no workspace in organization URL $organizationUrl")
            return ReviewingPullRequestListResult.Failed()
        }

        val apiToken = credentialStore.getToken()
        if (apiToken == null) {
            LOG.info("Cannot list the pull requests to review: no stored API token")
            return ReviewingPullRequestListResult.Failed()
        }

        // The reviewer filter is keyed by uuid, which the nickname-based route does not expose.
        val reviewerUuid = pullRequestClient.getCurrentUser(apiToken = apiToken)?.uuid
        if (reviewerUuid == null) {
            LOG.info("Cannot list the pull requests to review: the current Bitbucket user uuid is unknown")
            return ReviewingPullRequestListResult.Failed()
        }

        val targets = ReviewingPullRequestAssembler.repositoryTargets(
            repoSlugs = repoSlugs,
            excludedRepoSlug = excludedRepoSlug,
            maxRepositories = MAX_REPOSITORIES_PER_LOAD
        )

        val isCapped = repoSlugs.distinct().count { slug ->
            excludedRepoSlug == null || !slug.equals(excludedRepoSlug.trim(), ignoreCase = true)
        } > MAX_REPOSITORIES_PER_LOAD

        val results = coroutineScope {
            val permits = Semaphore(MAX_PARALLEL_REPOSITORY_REQUESTS)
            targets.map { repoSlug ->
                async {
                    permits.withPermit {
                        repoSlug to pullRequestClient.listOpenPullRequestsReviewedBy(
                            workspace = workspace,
                            repoSlug = repoSlug,
                            reviewerUuid = reviewerUuid,
                            pageLen = MAX_LISTED_PULL_REQUESTS,
                            apiToken = apiToken
                        )
                    }
                }
            }.awaitAll()
        }

        val failedResult = results.any { (_, result) -> result is ReviewerQueueQueryResult.Unauthorized }
        if (failedResult) {
            LOG.info("Cannot list the pull requests to review: Bitbucket rejected the API token")
            return ReviewingPullRequestListResult.Failed()
        }

        val queues = results.mapIndexed { index, (repoSlug, result) ->
            when (result) {
                is ReviewerQueueQueryResult.Listed -> RepositoryReviewQueue(repoSlug = repoSlug, pullRequests = result.pullRequests)
                is ReviewerQueueQueryResult.Skipped -> RepositoryReviewQueue(repoSlug = repoSlug, pullRequests = emptyList())
                is ReviewerQueueQueryResult.Failed -> RepositoryReviewQueue(repoSlug = repoSlug, pullRequests = null)
                else -> throw IllegalStateException("Unexpected ReviewerQueueQueryResult: $result")
            }
        }

        val (queried, skipped, failed) = queues.fold(Triple<Int, Int, Int>(0, 0, 0)) { (q, s, f), queue ->
            when {
                queue.pullRequests == null -> Triple(q, s, f + 1)
                queue.pullRequests.isEmpty() -> Triple(q, s + 1, f)
                else -> Triple(q + 1, s, f)
            }
        }
        LOG.info("Checked ${repoSlugs.size} watched repositories: queried $queried, skipped $skipped, failed $failed")

        // Attach the reviewer uuid to the success so the Mine and Approved-by-me filters can key off it. The uuid was
        // resolved above, so no extra request is made; an empty-repoSlugs load also flows through assemble and carries it.
        val assembled = ReviewingPullRequestAssembler.assemble(
            workspace = workspace,
            queues = queues,
            isRepositoryListTruncated = isCapped,
            maxPullRequests = MAX_LISTED_PULL_REQUESTS
        )
        return when (assembled) {
            is ReviewingPullRequestListResult.Success -> assembled.copy(currentUserUuid = reviewerUuid)
            is ReviewingPullRequestListResult.Failed -> assembled
        }
    }

    companion object {
        private val LOG = logger<BbCloudReviewQueueService>()

        /**
         * How many repositories one load may ask. A workspace can hold many watched projects, and every repository
         * costs one request, so the walk is bounded and says so through the result.
         */
        private const val MAX_REPOSITORIES_PER_LOAD = 50

        /**
         * How many rows the tab shows, and how many each repository contributes. Asking each repository for the same
         * number of rows as the table shows keeps the merged head correct; see [ReviewingPullRequestAssembler.assemble].
         */
        private const val MAX_LISTED_PULL_REQUESTS = 50

        private const val MAX_PARALLEL_REPOSITORY_REQUESTS = 4
    }
}