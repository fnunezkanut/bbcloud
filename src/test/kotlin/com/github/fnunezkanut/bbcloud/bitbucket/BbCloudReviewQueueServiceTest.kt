package com.github.fnunezkanut.bbcloud.bitbucket

import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class BbCloudReviewQueueServiceTest {
    private val organizationUrl = "https://bitbucket.org/my-workspace/"

    @Test
    fun `listReviewingPullRequests - should fail when the organization url holds no workspace`(): Unit =
        runBlocking {
            // GIVEN
            val service = service(client = mockk(), token = "token")

            // WHEN
            val result = service.listReviewingPullRequests(
                organizationUrl = "not-a-bitbucket-url",
                excludedRepoSlug = null,
                repoSlugs = listOf("repo-a")
            )

            // THEN
            result.shouldBeInstanceOf<ReviewingPullRequestListResult.Failed>()
        }

    @Test
    fun `listReviewingPullRequests - should fail when no token is stored`(): Unit =
        runBlocking {
            // GIVEN
            val service = service(client = mockk(), token = null)

            // WHEN
            val result = service.listReviewingPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null, repoSlugs = listOf("repo-a"))

            // THEN
            result.shouldBeInstanceOf<ReviewingPullRequestListResult.Failed>()
        }

    @Test
    fun `listReviewingPullRequests - should fail when the current user uuid is unknown`(): Unit =
        runBlocking {
            // GIVEN
            val client = mockk<BitbucketPullRequestClient> {
                coEvery { getCurrentUser(apiToken = any()) } returns null
            }
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listReviewingPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null, repoSlugs = listOf("repo-a"))

            // THEN
            result.shouldBeInstanceOf<ReviewingPullRequestListResult.Failed>()
        }

    @Test
    fun `listReviewingPullRequests - should query only the given repository slugs`(): Unit =
        runBlocking {
            // GIVEN two watched repositories
            val client = mockClientForSlugs(
                currentUserUuid = CURRENT_USER_UUID,
                slugResults = mapOf(
                    "repo-a" to ReviewerQueueQueryResult.Listed(listOf(pullRequest(1))),
                    "repo-b" to ReviewerQueueQueryResult.Listed(listOf(pullRequest(2)))
                )
            )
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listReviewingPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null, repoSlugs = listOf("repo-a", "repo-b"))

            // THEN only the given repositories are queried
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.pullRequests.map { it.summary.id } shouldContainExactly listOf(1, 2)
            success.pullRequests.map { it.repositoryName() } shouldContainExactly listOf("repo-a", "repo-b")
            coVerify(exactly = 0) {
                client.listOpenPullRequestsReviewedBy(
                    workspace = any(),
                    repoSlug = "not-watched",
                    reviewerUuid = any(),
                    pageLen = any(),
                    apiToken = any()
                )
            }
        }

    @Test
    fun `listReviewingPullRequests - should carry the reviewer uuid the queue was keyed by`(): Unit =
        runBlocking {
            // GIVEN
            val client = mockClientForSlugs(
                currentUserUuid = CURRENT_USER_UUID,
                slugResults = mapOf("repo-a" to ReviewerQueueQueryResult.Listed(listOf(pullRequest(1))))
            )
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listReviewingPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null, repoSlugs = listOf("repo-a"))

            // THEN the success carries the reviewer uuid, for the Mine and Approved-by-me filters
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.currentUserUuid shouldBe CURRENT_USER_UUID
        }

    @Test
    fun `listReviewingPullRequests - should skip the current project repository case-insensitively`(): Unit =
        runBlocking {
            // GIVEN
            val client = mockClientForSlugs(
                currentUserUuid = CURRENT_USER_UUID,
                slugResults = mapOf(
                    "repo-a" to ReviewerQueueQueryResult.Listed(listOf(pullRequest(1))),
                    "repo-b" to ReviewerQueueQueryResult.Listed(listOf(pullRequest(2)))
                )
            )
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listReviewingPullRequests(
                organizationUrl = organizationUrl,
                excludedRepoSlug = "Repo-A",
                repoSlugs = listOf("repo-a", "repo-b")
            )

            // THEN the excluded repo is not queried
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.pullRequests.map { it.summary.id } shouldContainExactly listOf(2)
            success.pullRequests.map { it.repositoryName() } shouldContainExactly listOf("repo-b")
            coVerify(exactly = 0) {
                client.listOpenPullRequestsReviewedBy(
                    workspace = any(),
                    repoSlug = "repo-a",
                    reviewerUuid = any(),
                    pageLen = any(),
                    apiToken = any()
                )
            }
        }

    @Test
    fun `listReviewingPullRequests - should query the repository slug exactly as given`(): Unit =
        runBlocking {
            // GIVEN a watched repository with an uppercase slug
            val client = mockClientForSlugs(
                currentUserUuid = CURRENT_USER_UUID,
                slugResults = mapOf(
                    "MyRepo" to ReviewerQueueQueryResult.Listed(listOf(pullRequest(1)))
                )
            )
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listReviewingPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null, repoSlugs = listOf("MyRepo"))

            // THEN the query uses "MyRepo" exactly
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.pullRequests.map { it.summary.id } shouldContainExactly listOf(1)
            success.pullRequests.map { it.repositoryName() } shouldContainExactly listOf("MyRepo")
            coVerify(exactly = 1) {
                client.listOpenPullRequestsReviewedBy(
                    workspace = any(),
                    repoSlug = "MyRepo",
                    reviewerUuid = any(),
                    pageLen = any(),
                    apiToken = any()
                )
            }
        }

    @Test
    fun `listReviewingPullRequests - should treat Skipped as an empty contribution`(): Unit =
        runBlocking {
            // GIVEN one repo skipped, one succeeds
            val client = mockClientForSlugs(
                currentUserUuid = CURRENT_USER_UUID,
                slugResults = mapOf(
                    "repo-a" to ReviewerQueueQueryResult.Skipped,
                    "repo-b" to ReviewerQueueQueryResult.Listed(listOf(pullRequest(1)))
                )
            )
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listReviewingPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null, repoSlugs = listOf("repo-a", "repo-b"))

            // THEN
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.pullRequests.map { it.summary.id } shouldContainExactly listOf(1)
            success.isTruncated shouldBe false
        }

    @Test
    fun `listReviewingPullRequests - should fail the whole load when any query returns Unauthorized`(): Unit =
        runBlocking {
            // GIVEN
            val client = mockClientForSlugs(
                currentUserUuid = CURRENT_USER_UUID,
                slugResults = mapOf(
                    "repo-a" to ReviewerQueueQueryResult.Unauthorized
                )
            )
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listReviewingPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null, repoSlugs = listOf("repo-a"))

            // THEN
            result.shouldBeInstanceOf<ReviewingPullRequestListResult.Failed>()
        }

    @Test
    fun `listReviewingPullRequests - should mark result truncated when a query returns Failed`(): Unit =
        runBlocking {
            // GIVEN one repo fails, one succeeds
            val client = mockClientForSlugs(
                currentUserUuid = CURRENT_USER_UUID,
                slugResults = mapOf(
                    "repo-a" to ReviewerQueueQueryResult.Failed(RuntimeException("network error")),
                    "repo-b" to ReviewerQueueQueryResult.Listed(listOf(pullRequest(1)))
                )
            )
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listReviewingPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null, repoSlugs = listOf("repo-a", "repo-b"))

            // THEN
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.pullRequests.map { it.summary.id } shouldContainExactly listOf(1)
            success.isTruncated shouldBe true
        }

    @Test
    fun `listReviewingPullRequests - should mark result truncated when there are more than 50 repositories`(): Unit =
        runBlocking {
            // GIVEN 51 watched repositories
            val slugs = (1..51).map { "repo-$it" }
            val client = mockClientForSlugs(
                currentUserUuid = CURRENT_USER_UUID,
                slugResults = (1..51).associate { i ->
                    "repo-$i" to ReviewerQueueQueryResult.Listed(listOf(pullRequest(i.toLong())))
                }
            )
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listReviewingPullRequests(
                organizationUrl = organizationUrl,
                excludedRepoSlug = null,
                repoSlugs = slugs
            )

            // THEN only 50 are queried, result is truncated
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.pullRequests.size shouldBe 50
            success.isTruncated shouldBe true
            coVerify(exactly = 50) {
                client.listOpenPullRequestsReviewedBy(
                    workspace = any(),
                    repoSlug = any(),
                    reviewerUuid = any(),
                    pageLen = any(),
                    apiToken = any()
                )
            }
        }

    @Test
    fun `listReviewingPullRequests - should return rows sorted newest first across repositories`(): Unit =
        runBlocking {
            // GIVEN two repos with PRs updated at different times
            val client = mockClientForSlugs(
                currentUserUuid = CURRENT_USER_UUID,
                slugResults = mapOf(
                    "older-repo" to ReviewerQueueQueryResult.Listed(listOf(pullRequest(1, updatedOn = "2026-09-01T12:00:00Z"))),
                    "newer-repo" to ReviewerQueueQueryResult.Listed(listOf(pullRequest(2, updatedOn = "2026-10-01T12:00:00Z")))
                )
            )
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listReviewingPullRequests(
                organizationUrl = organizationUrl,
                excludedRepoSlug = null,
                repoSlugs = listOf("older-repo", "newer-repo")
            )

            // THEN merged and sorted by updatedOn descending
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.pullRequests.map { it.summary.id } shouldContainExactly listOf(2, 1)
            success.isTruncated shouldBe false
        }

    @Test
    fun `listReviewingPullRequests - should return an empty success when there are no watched repositories`(): Unit =
        runBlocking {
            // GIVEN no watched repositories
            val client = mockClientForSlugs(
                currentUserUuid = CURRENT_USER_UUID,
                slugResults = emptyMap()
            )
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listReviewingPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null, repoSlugs = emptyList())

            // THEN
            val success = result.shouldBeInstanceOf<ReviewingPullRequestListResult.Success>()
            success.pullRequests.shouldBeEmpty()
            success.isTruncated shouldBe false
            coVerify(exactly = 0) {
                client.listOpenPullRequestsReviewedBy(
                    workspace = any(),
                    repoSlug = any(),
                    reviewerUuid = any(),
                    pageLen = any(),
                    apiToken = any()
                )
            }
        }

    private fun service(client: BitbucketPullRequestClient, token: String?): BbCloudReviewQueueService {
        val store = mockk<BbCloudCredentialStore> {
            coEvery { getToken() } returns token
        }
        return BbCloudReviewQueueService(credentialStore = store, pullRequestClient = client)
    }

    private fun mockClientForSlugs(currentUserUuid: String?, slugResults: Map<String, ReviewerQueueQueryResult>): BitbucketPullRequestClient {
        val client = mockk<BitbucketPullRequestClient>()
        coEvery { client.getCurrentUser(apiToken = any()) } returns currentUserUuid?.let { uuid -> CurrentUser(uuid = uuid, avatarUrl = null) }
        // The answer depends on which repository is being asked, resolved from the call arguments.
        coEvery {
            client.listOpenPullRequestsReviewedBy(
                workspace = any(),
                repoSlug = any(),
                reviewerUuid = any(),
                pageLen = any(),
                apiToken = any()
            )
        } answers {
            val repoSlug = secondArg<String>()
            slugResults[repoSlug] ?: ReviewerQueueQueryResult.Failed(RuntimeException("unexpected repoSlug: $repoSlug"))
        }
        return client
    }

    private fun pullRequest(id: Long, updatedOn: String = "2026-09-01T12:00:00Z"): PullRequestSummary {
        return PullRequestSummary(
            id = id,
            title = "PR $id",
            authorDisplayName = "John Smith",
            authorAvatarUrl = null,
            createdOn = Instant.parse(updatedOn),
            updatedOn = Instant.parse(updatedOn),
            sourceBranch = "feature/$id",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/my-workspace/repo/pull-requests/$id",
            state = PullRequestState.Open,
            isDraft = false
        )
    }

    companion object {
        private const val CURRENT_USER_UUID = "{5b10ac8d}"
    }
}