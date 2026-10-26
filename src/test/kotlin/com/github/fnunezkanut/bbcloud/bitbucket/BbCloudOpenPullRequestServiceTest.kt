package com.github.fnunezkanut.bbcloud.bitbucket

import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class BbCloudOpenPullRequestServiceTest {
    private val organizationUrl = "https://bitbucket.org/my-workspace/"

    @Test
    fun `listAllOpenPullRequests - should delegate the complete listing to the client`(): Unit =
        runBlocking {
            // GIVEN
            val expected = PullRequestListResult.Success(emptyList())
            val client = mockk<BitbucketPullRequestClient>()
            coEvery {
                client.listAllOpenPullRequests(
                    workspace = "my-workspace",
                    repoSlug = "examplerepo",
                    apiToken = "token"
                )
            } returns expected
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns "token"
            val service = BbCloudOpenPullRequestService(
                credentialStore = store,
                pullRequestClient = client,
                readRemoteUrl = { null }
            )

            // WHEN
            val result = service.listAllOpenPullRequests(
                projectBasePath = "/Users/me/Projects/examplerepo",
                organizationUrl = organizationUrl
            )

            // THEN
            result shouldBe expected
            coVerify(exactly = 1) {
                client.listAllOpenPullRequests(
                    workspace = "my-workspace",
                    repoSlug = "examplerepo",
                    apiToken = "token"
                )
            }
        }

    @Test
    fun `listRecentPullRequests - should return Failed when base path missing`(): Unit =
        runBlocking {
            val client = mockk<BitbucketPullRequestClient>()
            val store = mockk<BbCloudCredentialStore>()
            val service = BbCloudOpenPullRequestService(store, client)

            val result = service.listRecentPullRequests(
                projectBasePath = null,
                organizationUrl = organizationUrl
            )

            result.shouldBeInstanceOf<PullRequestListResult.Failed>()
        }

    @Test
    fun `listRecentPullRequests - should return Failed when token missing`(): Unit =
        runBlocking {
            val client = mockk<BitbucketPullRequestClient>()
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns null
            val service = BbCloudOpenPullRequestService(store, client)

            val result = service.listRecentPullRequests(
                projectBasePath = "/Users/me/Projects/examplerepo",
                organizationUrl = organizationUrl
            )

            result.shouldBeInstanceOf<PullRequestListResult.Failed>()
        }

    @Test
    fun `listRecentPullRequests - should delegate to client when configured`(): Unit =
        runBlocking {
            val expected = PullRequestListResult.Success(emptyList())
            val client = mockk<BitbucketPullRequestClient>()
            coEvery {
                client.listRecentPullRequests(
                    workspace = "my-workspace",
                    repoSlug = "examplerepo",
                    apiToken = "token"
                )
            } returns expected
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns "token"
            // No readable remote, so the slug falls back to the checkout folder name (the historical behavior).
            val service = BbCloudOpenPullRequestService(
                credentialStore = store,
                pullRequestClient = client,
                readRemoteUrl = { null }
            )

            val result = service.listRecentPullRequests(
                projectBasePath = "/Users/me/Projects/examplerepo",
                organizationUrl = organizationUrl
            )

            result shouldBe expected
            coVerify(exactly = 1) {
                client.listRecentPullRequests(
                    workspace = "my-workspace",
                    repoSlug = "examplerepo",
                    apiToken = "token"
                )
            }
        }

    @Test
    fun `listRecentPullRequests - should query the real slug from the remote for a worktree path`(): Unit =
        runBlocking {
            // GIVEN a worktree review window at $projectsDir/.worktrees/examplerepo--pr-7
            val expected = PullRequestListResult.Success(emptyList())
            val client = mockk<BitbucketPullRequestClient>()
            coEvery {
                client.listRecentPullRequests(
                    workspace = "my-workspace",
                    repoSlug = "examplerepo",
                    apiToken = "token"
                )
            } returns expected
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns "token"
            val worktreeRemote: (java.nio.file.Path) -> String? = { "https://bitbucket.org/my-workspace/examplerepo.git" }
            val service = BbCloudOpenPullRequestService(
                credentialStore = store,
                pullRequestClient = client,
                readRemoteUrl = worktreeRemote
            )

            // WHEN listing from inside the worktree window
            val result = service.listRecentPullRequests(
                projectBasePath = "/p/.worktrees/examplerepo--pr-7",
                organizationUrl = organizationUrl
            )

            // THEN the client is called with the real slug `examplerepo`, not the `examplerepo--pr-7` folder name
            result shouldBe expected
            coVerify(exactly = 1) {
                client.listRecentPullRequests(
                    workspace = "my-workspace",
                    repoSlug = "examplerepo",
                    apiToken = "token"
                )
            }
        }

    @Test
    fun `listRecentPullRequests - should pass the client order through untouched`(): Unit =
        runBlocking {
            // GIVEN
            val merged = pullRequest(id = 1, state = PullRequestState.Merged, updatedOn = "2026-09-30T12:00:00Z")
            val open = pullRequest(id = 2, state = PullRequestState.Open, updatedOn = "2026-09-01T12:00:00Z")
            val declined = pullRequest(id = 3, state = PullRequestState.Declined, updatedOn = "2026-09-29T12:00:00Z")
            val client = mockk<BitbucketPullRequestClient>()
            coEvery {
                client.listRecentPullRequests(
                    workspace = "my-workspace",
                    repoSlug = "examplerepo",
                    apiToken = "token"
                )
            } returns PullRequestListResult.Success(listOf(merged, open, declined))
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns "token"
            val service = BbCloudOpenPullRequestService(store, client)

            // WHEN
            val result = service.listRecentPullRequests(
                projectBasePath = "/Users/me/Projects/examplerepo",
                organizationUrl = organizationUrl
            )

            // THEN
            // Bitbucket answers newest-updated first; the split into open and closed happens for display.
            result shouldBe PullRequestListResult.Success(listOf(merged, open, declined))
        }

    @Nested
    inner class PullRequestCountsTests {
        private val basePath = "/Users/me/Projects/examplerepo"

        @Test
        fun `pullRequestCounts - should return both counts when client succeeds`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.countChangedFiles(workspace = "my-workspace", repoSlug = "examplerepo", pullRequestId = 273, apiToken = "token")
                } returns ItemCount(value = 1, isTruncated = false)
                coEvery {
                    client.countCommits(workspace = "my-workspace", repoSlug = "examplerepo", pullRequestId = 273, apiToken = "token")
                } returns ItemCount(value = 3, isTruncated = false)
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val counts = service.pullRequestCounts(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273
                )

                // THEN
                counts shouldBe PullRequestCounts(
                    filesChanged = ItemCount(value = 1, isTruncated = false),
                    commits = ItemCount(value = 3, isTruncated = false)
                )
            }

        @Test
        fun `pullRequestCounts - should keep files count when commits request fails`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.countChangedFiles(workspace = "my-workspace", repoSlug = "examplerepo", pullRequestId = 273, apiToken = "token")
                } returns ItemCount(value = 5, isTruncated = false)
                coEvery {
                    client.countCommits(workspace = "my-workspace", repoSlug = "examplerepo", pullRequestId = 273, apiToken = "token")
                } returns null
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val counts = service.pullRequestCounts(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273
                )

                // THEN
                assertSoftly(counts) {
                    filesChanged shouldBe ItemCount(value = 5, isTruncated = false)
                    commits.shouldBeNull()
                }
            }

        @Test
        fun `pullRequestCounts - should return empty counts without calling client when token missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val counts = service.pullRequestCounts(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273
                )

                // THEN
                counts shouldBe PullRequestCounts(filesChanged = null, commits = null)
                coVerify(exactly = 0) { client.countChangedFiles(any(), any(), any(), any()) }
                coVerify(exactly = 0) { client.countCommits(any(), any(), any(), any()) }
            }

        @Test
        fun `pullRequestCounts - should return empty counts when base path missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(mockk(), client)

                // WHEN
                val counts = service.pullRequestCounts(
                    projectBasePath = null,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273
                )

                // THEN
                counts shouldBe PullRequestCounts(filesChanged = null, commits = null)
            }

        private fun storeWithToken(token: String?): BbCloudCredentialStore {
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns token
            return store
        }
    }

    @Nested
    inner class PullRequestOverviewTests {
        private val basePath = "/Users/me/Projects/examplerepo"
        private val summary = pullRequest(id = 273, state = PullRequestState.Open, updatedOn = "2026-09-30T12:00:00Z")
        private val build = PullRequestBuildStatus(
            key = "jenkins",
            name = "examplerepo » feature/273 #1",
            state = BuildState.Failed,
            url = "https://ci/1",
            description = null,
            updatedOn = null
        )
        private val mergeCheck = PullRequestMergeCheck(
            kind = "minimum_approvals",
            name = null,
            status = MergeCheckStatus.Failed,
            isBlocking = false,
            requiredValue = 1,
            observedValue = 0
        )
        private val task = PullRequestTask(id = 7, content = "Update the changelog", isResolved = false)

        @Test
        fun `pullRequestOverview - should combine commits behind and builds when client succeeds`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.countCommitsBehind(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        sourceBranch = "feature/273",
                        destinationBranch = "main",
                        apiToken = "token"
                    )
                } returns ItemCount(value = 1, isTruncated = false)
                coEvery {
                    client.listBuildStatuses(workspace = "my-workspace", repoSlug = "examplerepo", pullRequestId = 273, apiToken = "token")
                } returns listOf(build)
                coEvery {
                    client.listMergeChecks(workspace = "my-workspace", repoSlug = "examplerepo", pullRequestId = 273, apiToken = "token")
                } returns listOf(mergeCheck)
                coEvery {
                    client.listTasks(workspace = "my-workspace", repoSlug = "examplerepo", pullRequestId = 273, apiToken = "token")
                } returns listOf(task)
                stubTimelineSources(client)
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val overview = service.pullRequestOverview(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    summary = summary
                )

                // THEN
                assertSoftly(overview) {
                    commitsBehind shouldBe ItemCount(value = 1, isTruncated = false)
                    builds shouldBe listOf(build)
                    mergeChecks shouldBe listOf(mergeCheck)
                    tasks shouldBe listOf(task)
                    conversation.shouldNotBeNull()
                    conversation.threads shouldBe emptyList()
                    conversation.currentUserUuid shouldBe "{me}"
                    conversation.currentUserAvatarUrl.shouldBeNull()
                    conversation.isTruncated shouldBe false
                    activityFeed.shouldNotBeNull()
                    activityFeed.entries.size shouldBe 1
                    activityFeed.entries.first().shouldBeInstanceOf<TimelineEntry.Opened>()
                    activityFeed.entries.first().date shouldBe summary.createdOn
                    activityFeed.isTruncated shouldBe false
                    activityFeed.hasPartialFailure shouldBe false
                }
            }

        @Test
        fun `pullRequestOverview - should pass the current user avatar into the conversation`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.countCommitsBehind(any(), any(), any(), any(), any()) } returns ItemCount(value = 0, isTruncated = false)
                coEvery { client.listBuildStatuses(any(), any(), any(), any()) } returns emptyList()
                coEvery { client.listMergeChecks(any(), any(), any(), any()) } returns emptyList()
                coEvery { client.listTasks(any(), any(), any(), any()) } returns emptyList()
                stubTimelineSources(client)
                coEvery { client.getCurrentUser(apiToken = "token") } returns CurrentUser(uuid = "{me}", avatarUrl = "https://avatars/me.png")
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val overview = service.pullRequestOverview(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    summary = summary
                )

                // THEN
                assertSoftly(overview.conversation.shouldNotBeNull()) {
                    currentUserUuid shouldBe "{me}"
                    currentUserAvatarUrl shouldBe "https://avatars/me.png"
                }
            }

        @Test
        fun `pullRequestOverview - should have no conversation when comments request fails`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.countCommitsBehind(any(), any(), any(), any(), any()) } returns ItemCount(value = 0, isTruncated = false)
                coEvery { client.listBuildStatuses(any(), any(), any(), any()) } returns listOf(build)
                coEvery { client.listMergeChecks(any(), any(), any(), any()) } returns emptyList()
                coEvery { client.listTasks(any(), any(), any(), any()) } returns emptyList()
                stubTimelineSources(client = client, comments = null)
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val overview = service.pullRequestOverview(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    summary = summary
                )

                // THEN
                assertSoftly(overview) {
                    builds shouldBe listOf(build)
                    conversation.shouldBeNull()
                    activityFeed.shouldNotBeNull()
                    activityFeed.hasPartialFailure shouldBe false
                }
            }

        @Test
        fun `pullRequestOverview - should build activity feed with partial failure when activity request fails`(): Unit =
            runBlocking {
                // GIVEN
                val approval = PullRequestActivityItem.Approval(
                    date = Instant.parse("2026-09-30T13:00:00Z"),
                    user = ActivityUser(uuid = "{reviewer}", displayName = "Jane Doe", avatarUrl = null)
                )
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.countCommitsBehind(any(), any(), any(), any(), any()) } returns ItemCount(value = 0, isTruncated = false)
                coEvery { client.listBuildStatuses(any(), any(), any(), any()) } returns emptyList()
                coEvery { client.listMergeChecks(any(), any(), any(), any()) } returns emptyList()
                coEvery { client.listTasks(any(), any(), any(), any()) } returns emptyList()
                stubTimelineSources(
                    client = client,
                    activity = null,
                    comments = PagedItems(items = emptyList(), isTruncated = false),
                    commits = PagedItems(items = emptyList(), isTruncated = false)
                )
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val overview = service.pullRequestOverview(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    summary = summary
                )

                // THEN
                val feed = overview.activityFeed.shouldNotBeNull()
                assertSoftly(feed) {
                    hasPartialFailure shouldBe true
                    entries.map { entry -> entry::class } shouldBe listOf(TimelineEntry.Opened::class)
                }
            }

        @Test
        fun `pullRequestOverview - should build activity feed with partial failure when commits request fails`(): Unit =
            runBlocking {
                // GIVEN
                val approval = PullRequestActivityItem.Approval(
                    date = Instant.parse("2026-09-30T13:00:00Z"),
                    user = ActivityUser(uuid = "{reviewer}", displayName = "Jane Doe", avatarUrl = null)
                )
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.countCommitsBehind(any(), any(), any(), any(), any()) } returns ItemCount(value = 0, isTruncated = false)
                coEvery { client.listBuildStatuses(any(), any(), any(), any()) } returns emptyList()
                coEvery { client.listMergeChecks(any(), any(), any(), any()) } returns emptyList()
                coEvery { client.listTasks(any(), any(), any(), any()) } returns emptyList()
                stubTimelineSources(client = client, activity = PagedItems(items = listOf(approval), isTruncated = false), commits = null)
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val overview = service.pullRequestOverview(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    summary = summary
                )

                // THEN
                val feed = overview.activityFeed.shouldNotBeNull()
                assertSoftly(feed) {
                    hasPartialFailure shouldBe true
                    entries.map { entry -> entry::class } shouldBe listOf(TimelineEntry.Approved::class, TimelineEntry.Opened::class)
                }
            }

        @Test
        fun `pullRequestOverview - should build conversation with threads and activity feed with partial failure when tasks request fails`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.countCommitsBehind(any(), any(), any(), any(), any()) } returns ItemCount(value = 0, isTruncated = false)
                coEvery { client.listBuildStatuses(any(), any(), any(), any()) } returns listOf(build)
                coEvery { client.listMergeChecks(any(), any(), any(), any()) } returns emptyList()
                coEvery { client.listTasks(any(), any(), any(), any()) } returns null
                stubTimelineSources(client)
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val overview = service.pullRequestOverview(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    summary = summary
                )

                // THEN
                assertSoftly(overview) {
                    builds shouldBe listOf(build)
                    tasks.shouldBeNull()
                    conversation.shouldNotBeNull()
                    activityFeed.shouldNotBeNull()
                    activityFeed.hasPartialFailure shouldBe true
                }
            }

        @Test
        fun `pullRequestOverview - should populate activity feed when comments request fails`(): Unit =
            runBlocking {
                // GIVEN
                val approval = PullRequestActivityItem.Approval(
                    date = Instant.parse("2026-09-30T13:00:00Z"),
                    user = ActivityUser(uuid = "{reviewer}", displayName = "Jane Doe", avatarUrl = null)
                )
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.countCommitsBehind(any(), any(), any(), any(), any()) } returns ItemCount(value = 0, isTruncated = false)
                coEvery { client.listBuildStatuses(any(), any(), any(), any()) } returns emptyList()
                coEvery { client.listMergeChecks(any(), any(), any(), any()) } returns emptyList()
                coEvery { client.listTasks(any(), any(), any(), any()) } returns emptyList()
                stubTimelineSources(client = client, activity = PagedItems(items = listOf(approval), isTruncated = false), comments = null)
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val overview = service.pullRequestOverview(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    summary = summary
                )

                // THEN
                assertSoftly(overview) {
                    conversation.shouldBeNull()
                    activityFeed.shouldNotBeNull()
                    activityFeed.hasPartialFailure shouldBe false
                    activityFeed.entries.map { entry -> entry::class } shouldBe listOf(TimelineEntry.Approved::class, TimelineEntry.Opened::class)
                }
            }

        @Test
        fun `pullRequestOverview - should return empty overview without calling client when token missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val overview = service.pullRequestOverview(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    summary = summary
                )

                // THEN
                overview shouldBe PullRequestOverview(commitsBehind = null, builds = null)
                coVerify(exactly = 0) { client.countCommitsBehind(any(), any(), any(), any(), any()) }
                coVerify(exactly = 0) { client.listBuildStatuses(any(), any(), any(), any()) }
                coVerify(exactly = 0) { client.listMergeChecks(any(), any(), any(), any()) }
                coVerify(exactly = 0) { client.listTasks(any(), any(), any(), any()) }
                coVerify(exactly = 0) { client.listActivity(any(), any(), any(), any()) }
            }

        private fun stubTimelineSources(
            client: BitbucketPullRequestClient,
            activity: PagedItems<PullRequestActivityItem>? = PagedItems(items = emptyList(), isTruncated = false),
            comments: PagedItems<PullRequestComment>? = PagedItems(items = emptyList(), isTruncated = false),
            commits: PagedItems<PullRequestCommit>? = PagedItems(items = emptyList(), isTruncated = false)
        ) {
            coEvery { client.listActivity(any(), any(), any(), any()) } returns activity
            coEvery { client.listComments(any(), any(), any(), any()) } returns comments
            coEvery { client.listPullRequestCommits(any(), any(), any(), any()) } returns commits
            coEvery { client.getCurrentUser(any()) } returns CurrentUser(uuid = "{me}", avatarUrl = null)
        }

        private fun storeWithToken(token: String?): BbCloudCredentialStore {
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns token
            return store
        }
    }

    @Nested
    inner class PullRequestActionTests {
        private val basePath = "/Users/me/Projects/examplerepo"
        private val summary = pullRequest(id = 273, state = PullRequestState.Open, updatedOn = "2026-09-30T12:00:00Z")
        private val userUuid = "{11111111-1111-1111-1111-111111111111}"

        @Test
        fun `performAction - should delegate to client when configured`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.performAction(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequest = summary,
                        action = PullRequestAction.Approve,
                        apiToken = "token"
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.performAction(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequest = summary,
                    action = PullRequestAction.Approve
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
            }

        @Test
        fun `performAction - should return Failed without calling client when token missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val result = service.performAction(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequest = summary,
                    action = PullRequestAction.Merge
                )

                // THEN
                result.shouldBeInstanceOf<PullRequestActionResult.Failed>()
                coVerify(exactly = 0) { client.performAction(any(), any(), any(), any(), any()) }
            }

        @Test
        fun `declinePullRequest - should decline before posting a trimmed top-level reason comment`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.performAction(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequest = summary,
                        action = PullRequestAction.Decline,
                        apiToken = "token"
                    )
                } returns PullRequestActionResult.Success
                coEvery {
                    client.createComment(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = summary.id,
                        content = "Declined: no longer needed",
                        apiToken = "token",
                        parentCommentId = null
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.declinePullRequest(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequest = summary,
                    reason = "  no longer needed  "
                )

                // THEN
                result shouldBe PullRequestDeclineResult.Success()
                coVerifyOrder {
                    client.performAction(any(), any(), any(), PullRequestAction.Decline, any())
                    client.createComment(any(), any(), any(), "Declined: no longer needed", any(), null)
                }
            }

        @Test
        fun `declinePullRequest - should skip comment for a blank reason`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.performAction(any(), any(), any(), PullRequestAction.Decline, any()) } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.declinePullRequest(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequest = summary,
                    reason = "  \t"
                )

                // THEN
                result shouldBe PullRequestDeclineResult.Success()
                coVerify(exactly = 0) { client.createComment(any(), any(), any(), any(), any(), any()) }
            }

        @Test
        fun `declinePullRequest - should not comment when decline fails`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val failure = PullRequestActionResult.Failed(message = "decline rejected")
                coEvery { client.performAction(any(), any(), any(), PullRequestAction.Decline, any()) } returns failure
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.declinePullRequest(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequest = summary,
                    reason = "reason"
                )

                // THEN
                result shouldBe PullRequestDeclineResult.Failed(failure = failure)
                coVerify(exactly = 0) { client.createComment(any(), any(), any(), any(), any(), any()) }
            }

        @Test
        fun `declinePullRequest - should keep decline successful when comment fails`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val commentFailure = PullRequestActionResult.Failed(message = "comment rejected")
                coEvery { client.performAction(any(), any(), any(), PullRequestAction.Decline, any()) } returns PullRequestActionResult.Success
                coEvery { client.createComment(any(), any(), any(), any(), any(), any()) } returns commentFailure
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.declinePullRequest(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequest = summary,
                    reason = "reason"
                )

                // THEN
                result shouldBe PullRequestDeclineResult.Success(commentFailure = commentFailure)
            }

        @Test
        fun `declinePullRequest - should not call client when token is missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val result = service.declinePullRequest(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequest = summary,
                    reason = "reason"
                )

                // THEN
                result.shouldBeInstanceOf<PullRequestDeclineResult.Failed>()
                coVerify(exactly = 0) { client.performAction(any(), any(), any(), any(), any()) }
            }

        @Test
        fun `pullRequestDetails - should fetch current user once and reuse it`(): Unit =
            runBlocking {
                // GIVEN
                val details = PullRequestDetails(summary = summary, isApprovedByCurrentUser = true)
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.getCurrentUser(apiToken = "token") } returns CurrentUser(uuid = userUuid, avatarUrl = null)
                coEvery {
                    client.getPullRequestDetails(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        currentUserUuid = userUuid,
                        apiToken = "token"
                    )
                } returns details
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val first = service.pullRequestDetails(projectBasePath = basePath, organizationUrl = organizationUrl, pullRequestId = 273)
                val second = service.pullRequestDetails(projectBasePath = basePath, organizationUrl = organizationUrl, pullRequestId = 273)

                // THEN
                assertSoftly {
                    first shouldBe details
                    second shouldBe details
                }
                coVerify(exactly = 1) { client.getCurrentUser(apiToken = "token") }
            }

        @Test
        fun `pullRequestDetails - should return null without calling client when token missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val details = service.pullRequestDetails(projectBasePath = basePath, organizationUrl = organizationUrl, pullRequestId = 273)

                // THEN
                details.shouldBeNull()
                coVerify(exactly = 0) { client.getCurrentUser(any()) }
            }

        private fun storeWithToken(token: String?): BbCloudCredentialStore {
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns token
            return store
        }
    }

    @Nested
    inner class DescriptionTests {
        private val basePath = "/Users/me/Projects/examplerepo"

        @Test
        fun `updateDescription - should preserve the title and forward raw Markdown`() =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.updatePullRequestDescription(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        title = "PR 273",
                        description = "## Updated",
                        apiToken = "token"
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.updateDescription(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    title = "PR 273",
                    description = "## Updated"
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
                coVerify(exactly = 1) {
                    client.updatePullRequestDescription(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        title = "PR 273",
                        description = "## Updated",
                        apiToken = "token"
                    )
                }
            }

        @Test
        fun `updateDescription - should return Failed without calling client when token missing`() =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val result = service.updateDescription(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    title = "PR 273",
                    description = "Updated"
                )

                // THEN
                result.shouldBeInstanceOf<PullRequestActionResult.Failed>()
                coVerify(exactly = 0) { client.updatePullRequestDescription(any(), any(), any(), any(), any(), any()) }
            }

        private fun storeWithToken(token: String?): BbCloudCredentialStore {
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns token
            return store
        }
    }

    @Nested
    inner class ReviewerTests {
        private val basePath = "/Users/me/Projects/examplerepo"
        private val summary = pullRequest(id = 273, state = PullRequestState.Open, updatedOn = "2026-09-30T12:00:00Z")
        private val existingReviewer = PullRequestReviewer(uuid = "{a}", displayName = "Anna", avatarUrl = null, status = ReviewerStatus.None)
        private val otherReviewer = PullRequestReviewer(uuid = "{b}", displayName = "Ben", avatarUrl = null, status = ReviewerStatus.Approved)

        @Test
        fun `updateReviewer - should append the added reviewer to the current reviewers`(): Unit =
            runBlocking {
                // GIVEN
                val client = clientWithReviewers(existingReviewer)
                coEvery {
                    client.updateReviewers(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        title = "PR 273",
                        reviewerUuids = listOf("{a}", "{c}"),
                        apiToken = "token"
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.updateReviewer(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = ReviewerChange.Add("{c}")
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
            }

        @Test
        fun `updateReviewer - should keep the other reviewers when removing one`(): Unit =
            runBlocking {
                // GIVEN
                val client = clientWithReviewers(existingReviewer, otherReviewer)
                coEvery { client.updateReviewers(any(), any(), any(), any(), any(), any()) } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                service.updateReviewer(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = ReviewerChange.Remove("{a}")
                )

                // THEN
                coVerify(exactly = 1) {
                    client.updateReviewers(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        title = "PR 273",
                        reviewerUuids = listOf("{b}"),
                        apiToken = "token"
                    )
                }
            }

        @Test
        fun `updateReviewer - should not send an update when the reviewer is already present`(): Unit =
            runBlocking {
                // GIVEN
                val client = clientWithReviewers(existingReviewer)
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.updateReviewer(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = ReviewerChange.Add("{a}")
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
                coVerify(exactly = 0) { client.updateReviewers(any(), any(), any(), any(), any(), any()) }
            }

        @Test
        fun `updateReviewer - should fail without updating when current reviewers cannot be loaded`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.getPullRequestDetails(any(), any(), any(), any(), any()) } returns null
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.updateReviewer(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = ReviewerChange.Add("{c}")
                )

                // THEN
                result.shouldBeInstanceOf<PullRequestActionResult.Failed>()
                coVerify(exactly = 0) { client.updateReviewers(any(), any(), any(), any(), any(), any()) }
            }

        @Test
        fun `reviewerCandidates - should fetch workspace members once and reuse them`(): Unit =
            runBlocking {
                // GIVEN
                val member = ReviewerCandidate(uuid = "{c}", displayName = "Carla", nickname = "carla", avatarUrl = null)
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.listWorkspaceMembers(workspace = "my-workspace", apiToken = "token")
                } returns WorkspaceMembers(members = listOf(member), isTruncated = false)
                coEvery {
                    client.listEffectiveDefaultReviewerUuids(workspace = "my-workspace", repoSlug = "examplerepo", apiToken = "token")
                } returns setOf("{c}")
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val first = service.reviewerCandidates(projectBasePath = basePath, organizationUrl = organizationUrl)
                val second = service.reviewerCandidates(projectBasePath = basePath, organizationUrl = organizationUrl)

                // THEN
                val expected = ReviewerCandidates(members = listOf(member), defaultReviewerUuids = setOf("{c}"), isTruncated = false)
                assertSoftly {
                    first shouldBe expected
                    second shouldBe expected
                }
                coVerify(exactly = 1) { client.listWorkspaceMembers(any(), any()) }
            }

        @Test
        fun `reviewerCandidates - should return members without suggestions when default reviewers fail`(): Unit =
            runBlocking {
                // GIVEN
                val member = ReviewerCandidate(uuid = "{c}", displayName = "Carla", nickname = null, avatarUrl = null)
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.listWorkspaceMembers(any(), any()) } returns WorkspaceMembers(members = listOf(member), isTruncated = true)
                coEvery { client.listEffectiveDefaultReviewerUuids(any(), any(), any()) } returns null
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val candidates = service.reviewerCandidates(projectBasePath = basePath, organizationUrl = organizationUrl)

                // THEN
                candidates shouldBe ReviewerCandidates(members = listOf(member), defaultReviewerUuids = emptySet(), isTruncated = true)
            }

        @Test
        fun `reviewerCandidates - should return null when workspace members fail`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.listWorkspaceMembers(any(), any()) } returns null
                coEvery { client.listEffectiveDefaultReviewerUuids(any(), any(), any()) } returns setOf("{c}")
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val candidates = service.reviewerCandidates(projectBasePath = basePath, organizationUrl = organizationUrl)

                // THEN
                candidates.shouldBeNull()
            }

        private fun clientWithReviewers(vararg reviewers: PullRequestReviewer): BitbucketPullRequestClient {
            val client = mockk<BitbucketPullRequestClient>()
            coEvery {
                client.getPullRequestDetails(
                    workspace = "my-workspace",
                    repoSlug = "examplerepo",
                    pullRequestId = 273,
                    currentUserUuid = null,
                    apiToken = "token"
                )
            } returns PullRequestDetails(summary = summary, isApprovedByCurrentUser = false, reviewers = reviewers.toList())
            return client
        }

        private fun storeWithToken(token: String?): BbCloudCredentialStore {
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns token
            return store
        }
    }

    @Nested
    inner class TaskChangeTests {
        private val basePath = "/Users/me/Projects/examplerepo"

        @Test
        fun `applyTaskChange - should create the task when configured`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.createTask(workspace = "my-workspace", repoSlug = "examplerepo", pullRequestId = 273, content = "Add tests", apiToken = "token")
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.applyTaskChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = TaskChange.Create("Add tests")
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
            }

        @Test
        fun `applyTaskChange - should update only the state of every task when resolving`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.updateTask(any(), any(), any(), any(), any(), any(), any()) } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.applyTaskChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = TaskChange.SetResolved(taskIds = listOf(1, 2), resolved = true)
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
                listOf(1L, 2L).forEach { taskId ->
                    coVerify(exactly = 1) {
                        client.updateTask(
                            workspace = "my-workspace",
                            repoSlug = "examplerepo",
                            pullRequestId = 273,
                            taskId = taskId,
                            content = null,
                            resolved = true,
                            apiToken = "token"
                        )
                    }
                }
            }

        @Test
        fun `applyTaskChange - should update text and then delete tasks when saving edits`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.updateTask(any(), any(), any(), any(), any(), any(), any()) } returns PullRequestActionResult.Success
                coEvery { client.deleteTask(any(), any(), any(), any(), any()) } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)
                val edits = TaskEdits(contentUpdates = mapOf(1L to "Renamed"), deletedIds = setOf(2L))

                // WHEN
                val result = service.applyTaskChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = TaskChange.Edit(edits)
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
                coVerifyOrder {
                    client.updateTask(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        taskId = 1,
                        content = "Renamed",
                        resolved = null,
                        apiToken = "token"
                    )
                    client.deleteTask(workspace = "my-workspace", repoSlug = "examplerepo", pullRequestId = 273, taskId = 2, apiToken = "token")
                }
            }

        @Test
        fun `applyTaskChange - should stop at the first failure`(): Unit =
            runBlocking {
                // GIVEN
                val failure = PullRequestActionResult.Failed(message = "Forbidden")
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.updateTask(any(), any(), any(), taskId = 1, any(), any(), any()) } returns failure
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)
                val edits = TaskEdits(contentUpdates = mapOf(1L to "Renamed"), deletedIds = setOf(2L))

                // WHEN
                val result = service.applyTaskChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = TaskChange.Edit(edits)
                )

                // THEN
                result shouldBe failure
                coVerify(exactly = 0) { client.deleteTask(any(), any(), any(), any(), any()) }
            }

        @Test
        fun `applyTaskChange - should return Failed without calling client when token missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val result = service.applyTaskChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = TaskChange.Create("Add tests")
                )

                // THEN
                result.shouldBeInstanceOf<PullRequestActionResult.Failed>()
                coVerify(exactly = 0) { client.createTask(any(), any(), any(), any(), any()) }
            }

        private fun storeWithToken(token: String?): BbCloudCredentialStore {
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns token
            return store
        }
    }

    @Nested
    inner class CommentTests {
        private val basePath = "/Users/me/Projects/examplerepo"

        @Test
        fun `addComment - should create the comment when configured`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.createComment(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        content = "**Looks good**",
                        apiToken = "token",
                        parentCommentId = null
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.addComment(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    content = "**Looks good**"
                )

                // THEN a top level comment carries no parent
                result shouldBe PullRequestActionResult.Success
                coVerify(exactly = 1) {
                    client.createComment(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        content = "**Looks good**",
                        apiToken = "token",
                        parentCommentId = null
                    )
                }
            }

        @Test
        fun `addComment - should return the client failure`(): Unit =
            runBlocking {
                // GIVEN
                val failure = PullRequestActionResult.Failed(message = "Forbidden")
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.createComment(any(), any(), any(), any(), any(), any()) } returns failure
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.addComment(projectBasePath = basePath, organizationUrl = organizationUrl, pullRequestId = 273, content = "Hi")

                // THEN
                result shouldBe failure
            }

        @Test
        fun `addComment - should return Failed without calling client when token missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val result = service.addComment(projectBasePath = basePath, organizationUrl = organizationUrl, pullRequestId = 273, content = "Hi")

                // THEN
                result.shouldBeInstanceOf<PullRequestActionResult.Failed>()
                coVerify(exactly = 0) { client.createComment(any(), any(), any(), any(), any(), any()) }
            }

        @Test
        fun `applyCommentChange - should create a path-only file-scoped comment`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.createComment(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        content = "Review this file",
                        apiToken = "token",
                        parentCommentId = null,
                        inlinePath = "src/Foo.kt"
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.applyCommentChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = CommentChange.CreateFileScoped(content = "Review this file", path = "src/Foo.kt")
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
                coVerify(exactly = 1) {
                    client.createComment(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        content = "Review this file",
                        apiToken = "token",
                        parentCommentId = null,
                        inlinePath = "src/Foo.kt"
                    )
                }
            }

        @Test
        fun `applyCommentChange - should anchor an AFTER-side line comment with inline to`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.createComment(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        content = "Nit here",
                        apiToken = "token",
                        parentCommentId = null,
                        inlinePath = "src/Foo.kt",
                        inlineLine = 42,
                        inlineSide = InlineCommentSide.AFTER
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.applyCommentChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = CommentChange.CreateInlineLine(
                        content = "Nit here",
                        path = "src/Foo.kt",
                        line = 42,
                        side = InlineCommentSide.AFTER
                    )
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
                coVerify(exactly = 1) {
                    client.createComment(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        content = "Nit here",
                        apiToken = "token",
                        parentCommentId = null,
                        inlinePath = "src/Foo.kt",
                        inlineLine = 42,
                        inlineSide = InlineCommentSide.AFTER
                    )
                }
            }

        @Test
        fun `applyCommentChange - should anchor a BEFORE-side line comment with inline from`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.createComment(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        content = "Was this removed?",
                        apiToken = "token",
                        parentCommentId = null,
                        inlinePath = "src/Foo.kt",
                        inlineLine = 7,
                        inlineSide = InlineCommentSide.BEFORE
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.applyCommentChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = CommentChange.CreateInlineLine(
                        content = "Was this removed?",
                        path = "src/Foo.kt",
                        line = 7,
                        side = InlineCommentSide.BEFORE
                    )
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
                coVerify(exactly = 1) {
                    client.createComment(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        content = "Was this removed?",
                        apiToken = "token",
                        parentCommentId = null,
                        inlinePath = "src/Foo.kt",
                        inlineLine = 7,
                        inlineSide = InlineCommentSide.BEFORE
                    )
                }
            }

        @Test
        fun `applyCommentChange - should fail a line comment with a blank path without calling the client`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.applyCommentChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = CommentChange.CreateInlineLine(content = "Note", path = "   ", line = 3, side = InlineCommentSide.AFTER)
                )

                // THEN
                result.shouldBeInstanceOf<PullRequestActionResult.Failed>()
                coVerify(exactly = 0) { client.createComment(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            }

        @Test
        fun `applyCommentChange - should carry the parent comment id of a reply`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.createComment(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        content = "Agreed",
                        apiToken = "token",
                        parentCommentId = 17
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.applyCommentChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = CommentChange.Reply(commentId = 17, content = "Agreed")
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
            }

        @Test
        fun `applyCommentChange - should update the content without resolving when editing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.updateComment(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        commentId = 17,
                        content = "Renamed",
                        apiToken = "token"
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.applyCommentChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = CommentChange.Edit(commentId = 17, content = "Renamed")
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
            }

        @Test
        fun `applyCommentChange - should resolve through the resolve endpoint`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.resolveCommentThread(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        commentId = 17,
                        apiToken = "token"
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.applyCommentChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = CommentChange.Resolve(commentId = 17)
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
                coVerify(exactly = 1) {
                    client.resolveCommentThread(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        commentId = 17,
                        apiToken = "token"
                    )
                }
            }

        @Test
        fun `applyCommentChange - should reopen through the resolve endpoint`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.reopenCommentThread(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        commentId = 17,
                        apiToken = "token"
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.applyCommentChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = CommentChange.Unresolve(commentId = 17)
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
            }

        @Test
        fun `applyCommentChange - should return Failed without calling client when token missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val result = service.applyCommentChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = CommentChange.Resolve(commentId = 17)
                )

                // THEN
                result.shouldBeInstanceOf<PullRequestActionResult.Failed>()
                coVerify(exactly = 0) { client.resolveCommentThread(any(), any(), any(), any(), any()) }
                coVerify(exactly = 0) { client.reopenCommentThread(any(), any(), any(), any(), any()) }
            }

        @Test
        fun `applyCommentChange - should delete a comment`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.deleteComment(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        commentId = 17,
                        apiToken = "token"
                    )
                } returns PullRequestActionResult.Success
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.applyCommentChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = CommentChange.Delete(commentId = 17)
                )

                // THEN
                result shouldBe PullRequestActionResult.Success
            }

        @Test
        fun `applyCommentChange - should return the delete client failure`(): Unit =
            runBlocking {
                // GIVEN
                val failure = PullRequestActionResult.Failed(message = "Forbidden")
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.deleteComment(any(), any(), any(), any(), any()) } returns failure
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.applyCommentChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = CommentChange.Delete(commentId = 17)
                )

                // THEN
                result shouldBe failure
            }

        @Test
        fun `applyCommentChange - delete should return Failed without calling client when token missing`() =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val result = service.applyCommentChange(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273,
                    change = CommentChange.Delete(commentId = 17)
                )

                // THEN
                result.shouldBeInstanceOf<PullRequestActionResult.Failed>()
                coVerify(exactly = 0) { client.deleteComment(any(), any(), any(), any(), any()) }
            }

        private fun storeWithToken(token: String?): BbCloudCredentialStore {
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns token
            return store
        }
    }

    @Nested
    inner class PullRequestCommitsTests {
        private val basePath = "/Users/me/Projects/examplerepo"
        private val commit = PullRequestCommit(
            hash = "abc123",
            message = "Add the review bar",
            date = Instant.parse("2026-09-30T12:00:00Z"),
            author = ActivityUser(uuid = "{author}", displayName = "John Smith", avatarUrl = null),
            url = null
        )

        @Test
        fun `pullRequestCommits - should return the client result and delegate exactly once`(): Unit =
            runBlocking {
                // GIVEN
                val expected = PagedItems(items = listOf(commit), isTruncated = false)
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.listPullRequestCommits(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        apiToken = "token"
                    )
                } returns expected
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val commits = service.pullRequestCommits(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273
                )

                // THEN
                commits shouldBe expected
                coVerify(exactly = 1) {
                    client.listPullRequestCommits(
                        workspace = "my-workspace",
                        repoSlug = "examplerepo",
                        pullRequestId = 273,
                        apiToken = "token"
                    )
                }
            }

        @Test
        fun `pullRequestCommits - should return null without calling client when token missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val commits = service.pullRequestCommits(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    pullRequestId = 273
                )

                // THEN
                commits.shouldBeNull()
                coVerify(exactly = 0) { client.listPullRequestCommits(any(), any(), any(), any()) }
            }

        private fun storeWithToken(token: String?): BbCloudCredentialStore {
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns token
            return store
        }
    }

    @Nested
    inner class NewPullRequestTests {
        private val basePath = "/Users/me/Projects/examplerepo"

        @Test
        fun `repositoryBranches - should return null without calling client when token missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val result = service.repositoryBranches(projectBasePath = basePath, organizationUrl = organizationUrl)

                // THEN
                result.shouldBeNull()
                coVerify(exactly = 0) { client.listBranches(any(), any(), any()) }
            }

        @Test
        fun `repositoryBranches - should load branches and main branch for the resolved repository`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery { client.listBranches("my-workspace", "examplerepo", "token") } returns
                    RepositoryBranches(names = listOf("main", "feature/x"), isTruncated = false)
                coEvery { client.getMainBranchName("my-workspace", "examplerepo", "token") } returns "main"
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.repositoryBranches(projectBasePath = basePath, organizationUrl = organizationUrl)

                // THEN
                assertSoftly {
                    result?.branches?.names shouldBe listOf("main", "feature/x")
                    result?.mainBranchName shouldBe "main"
                }
            }

        @Test
        fun `findOpenPullRequest - should return Failed without calling client when token missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val result = service.findOpenPullRequest(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    sourceBranch = "feature/x",
                    destinationBranch = "main"
                )

                // THEN
                result.shouldBeInstanceOf<ExistingPullRequestLookupResult.Failed>()
                coVerify(exactly = 0) { client.findOpenPullRequest(any(), any(), any(), any(), any()) }
            }

        @Test
        fun `findOpenPullRequest - should delegate to the client with the resolved repository`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                coEvery {
                    client.findOpenPullRequest("my-workspace", "examplerepo", "feature/x", "main", "token")
                } returns ExistingPullRequestLookupResult.None
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.findOpenPullRequest(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    sourceBranch = "feature/x",
                    destinationBranch = "main"
                )

                // THEN
                result shouldBe ExistingPullRequestLookupResult.None
            }

        @Test
        fun `createPullRequest - should return Failed without calling client when token missing`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val service = BbCloudOpenPullRequestService(storeWithToken(null), client)

                // WHEN
                val result = service.createPullRequest(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    request = newRequest()
                )

                // THEN
                result.shouldBeInstanceOf<CreatePullRequestResult.Failed>()
                coVerify(exactly = 0) { client.createPullRequest(any(), any(), any(), any()) }
            }

        @Test
        fun `createPullRequest - should delegate to the client with the resolved repository`(): Unit =
            runBlocking {
                // GIVEN
                val client = mockk<BitbucketPullRequestClient>()
                val request = newRequest()
                val created = CreatePullRequestResult.Created(summary = pullRequest(id = 5, state = PullRequestState.Open, updatedOn = "2026-09-30T12:00:00Z"))
                coEvery { client.createPullRequest("my-workspace", "examplerepo", request, "token") } returns created
                val service = BbCloudOpenPullRequestService(storeWithToken("token"), client)

                // WHEN
                val result = service.createPullRequest(
                    projectBasePath = basePath,
                    organizationUrl = organizationUrl,
                    request = request
                )

                // THEN
                result shouldBe created
            }

        private fun newRequest(): NewPullRequest {
            return NewPullRequest(
                title = "a title",
                description = "",
                sourceBranch = "feature/x",
                destinationBranch = "main",
                reviewerUuids = emptyList(),
                closeSourceBranch = true,
                isDraft = false
            )
        }

        private fun storeWithToken(token: String?): BbCloudCredentialStore {
            val store = mockk<BbCloudCredentialStore>()
            coEvery { store.getToken() } returns token
            return store
        }
    }

    private fun pullRequest(id: Long, state: PullRequestState, updatedOn: String): PullRequestSummary {
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
            webUrl = "https://bitbucket.org/workspace/repo/pull-requests/$id",
            state = state,
            isDraft = false
        )
    }
}