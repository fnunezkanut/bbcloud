package com.github.fnunezkanut.bbcloud.bitbucket

import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
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
class BbCloudAuthoredPullRequestServiceTest {
    private val organizationUrl = "https://bitbucket.org/my-workspace/"

    @Test
    fun `listAuthoredPullRequests - should fail when the organization url holds no workspace`(): Unit =
        runBlocking {
            // GIVEN
            val service = service(client = mockk(), token = "token")

            // WHEN
            val result = service.listAuthoredPullRequests(organizationUrl = "not-a-bitbucket-url", excludedRepoSlug = null)

            // THEN
            result.shouldBeInstanceOf<AuthoredPullRequestListResult.Failed>()
        }

    @Test
    fun `listAuthoredPullRequests - should fail when no token is stored`(): Unit =
        runBlocking {
            // GIVEN
            val service = service(client = mockk(), token = null)

            // WHEN
            val result = service.listAuthoredPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null)

            // THEN
            result.shouldBeInstanceOf<AuthoredPullRequestListResult.Failed>()
        }

    @Test
    fun `listAuthoredPullRequests - should fail when the current user uuid is unknown`(): Unit =
        runBlocking {
            // GIVEN
            val client = mockk<BitbucketPullRequestClient> {
                coEvery { getCurrentUser(apiToken = any()) } returns null
            }
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listAuthoredPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null)

            // THEN
            result.shouldBeInstanceOf<AuthoredPullRequestListResult.Failed>()
        }

    @Test
    fun `listAuthoredPullRequests - should fail when the listing cannot be read`(): Unit =
        runBlocking {
            // GIVEN
            val service = service(client = clientReturning(page = null), token = "token")

            // WHEN
            val result = service.listAuthoredPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null)

            // THEN
            result.shouldBeInstanceOf<AuthoredPullRequestListResult.Failed>()
        }

    @Test
    fun `listAuthoredPullRequests - should ask the workspace for the pull requests of the current user uuid`(): Unit =
        runBlocking {
            // GIVEN
            val client = clientReturning(page = page(entries = listOf(entry(repoSlug = "other-repo"))))
            val service = service(client = client, token = "token")

            // WHEN
            service.listAuthoredPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null)

            // THEN
            coVerify(exactly = 1) {
                client.listOpenAuthoredPullRequestsInWorkspace(
                    workspace = "my-workspace",
                    selectedUserUuid = CURRENT_USER_UUID,
                    apiToken = "token"
                )
            }
        }

    @Test
    fun `listAuthoredPullRequests - should list the pull requests with the repository they belong to`(): Unit =
        runBlocking {
            // GIVEN
            val client = clientReturning(
                page = page(
                    entries = listOf(
                        entry(id = 1, repoSlug = "other-repo"),
                        entry(id = 2, repoSlug = "third-repo")
                    )
                )
            )
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listAuthoredPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null)

            // THEN
            val success = result.shouldBeInstanceOf<AuthoredPullRequestListResult.Success>()
            success.pullRequests.map { pullRequest -> pullRequest.summary.id } shouldContainExactly listOf(1L, 2L)
            success.pullRequests.map { pullRequest -> pullRequest.repositoryName() } shouldContainExactly
                listOf("other-repo", "third-repo")
            success.isTruncated shouldBe false
        }

    @Test
    fun `listAuthoredPullRequests - should skip the current project repository`(): Unit =
        runBlocking {
            // GIVEN two repositories, one of them the current project
            val client = clientReturning(
                page = page(
                    entries = listOf(
                        entry(id = 1, repoSlug = "another-repo"),
                        entry(id = 2, repoSlug = "other-repo")
                    )
                )
            )
            val service = service(client = client, token = "token")

            // WHEN the excluded slug arrives with the capitalisation of the local directory
            val result = service.listAuthoredPullRequests(
                organizationUrl = organizationUrl,
                excludedRepoSlug = "Another-Repo"
            )

            // THEN only the other repository is listed
            val success = result.shouldBeInstanceOf<AuthoredPullRequestListResult.Success>()
            success.pullRequests.map { pullRequest -> pullRequest.summary.id } shouldContainExactly listOf(2L)
        }

    @Test
    fun `listAuthoredPullRequests - should succeed with nothing when only the current project remains`(): Unit =
        runBlocking {
            // GIVEN the workspace holds just the repository of the current project
            val client = clientReturning(page = page(entries = listOf(entry(repoSlug = "another-repo"))))
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listAuthoredPullRequests(
                organizationUrl = organizationUrl,
                excludedRepoSlug = "another-repo"
            )

            // THEN this is an empty listing rather than a failure
            val success = result.shouldBeInstanceOf<AuthoredPullRequestListResult.Success>()
            success.pullRequests shouldBe emptyList()
            success.isTruncated shouldBe false
        }

    @Test
    fun `listAuthoredPullRequests - should report truncation when the page was cut short`(): Unit =
        runBlocking {
            // GIVEN more pages were on offer than were read
            val client = clientReturning(page = page(entries = listOf(entry(repoSlug = "other-repo")), isTruncated = true))
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listAuthoredPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null)

            // THEN the rows still stand, but the listing says it could not be complete
            val success = result.shouldBeInstanceOf<AuthoredPullRequestListResult.Success>()
            success.pullRequests.size shouldBe 1
            success.isTruncated shouldBe true
        }

    @Test
    fun `listAuthoredPullRequests - should drop rows that named no repository and report the listing as incomplete`(): Unit =
        runBlocking {
            // GIVEN one row whose links named no repository
            val client = clientReturning(
                page = page(
                    entries = listOf(entry(repoSlug = "other-repo"), entry(id = 2, repoSlug = null))
                )
            )
            val service = service(client = client, token = "token")

            // WHEN
            val result = service.listAuthoredPullRequests(organizationUrl = organizationUrl, excludedRepoSlug = null)

            // THEN the readable row survives on its own
            val success = result.shouldBeInstanceOf<AuthoredPullRequestListResult.Success>()
            success.pullRequests.map { pullRequest -> pullRequest.summary.id } shouldContainExactly listOf(1L)
            success.isTruncated shouldBe true
        }

    private fun service(client: BitbucketPullRequestClient, token: String?): BbCloudAuthoredPullRequestService {
        val store = mockk<BbCloudCredentialStore> {
            coEvery { getToken() } returns token
        }
        return BbCloudAuthoredPullRequestService(credentialStore = store, pullRequestClient = client)
    }

    private fun clientReturning(page: AuthoredPullRequestPage?): BitbucketPullRequestClient {
        return mockk<BitbucketPullRequestClient> {
            coEvery { getCurrentUser(apiToken = any()) } returns CurrentUser(uuid = CURRENT_USER_UUID, avatarUrl = null)
            coEvery { listOpenAuthoredPullRequestsInWorkspace(workspace = any(), selectedUserUuid = any(), apiToken = any()) } returns page
        }
    }

    private fun page(entries: List<AuthoredPullRequestEntry>, isTruncated: Boolean = false): AuthoredPullRequestPage {
        return AuthoredPullRequestPage(entries = entries, isTruncated = isTruncated)
    }

    private fun entry(id: Long = 1, repoSlug: String?): AuthoredPullRequestEntry {
        return AuthoredPullRequestEntry(summary = pullRequest(id = id), repoSlug = repoSlug)
    }

    private fun pullRequest(id: Long): PullRequestSummary {
        return PullRequestSummary(
            id = id,
            title = "PR $id",
            authorDisplayName = "John Smith",
            authorAvatarUrl = null,
            createdOn = Instant.parse("2026-09-01T12:00:00Z"),
            updatedOn = Instant.parse("2026-09-01T12:00:00Z"),
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