package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BitbucketReviewerQueueClientTest {
    @Nested
    inner class ReviewingPullRequests {
        @Test
        fun `listOpenPullRequestsReviewedBy - should return Listed with parsed summaries on 200`(): Unit =
            runBlocking {
                // GIVEN a repository whose open review queue arrives on one page
                val requests = mutableListOf<Request>()
                val client = clientReturning(
                    code = 200,
                    body = """{"values":[$PULL_REQUEST_JSON]}""",
                    captured = requests
                )

                // WHEN
                val result = client.listOpenPullRequestsReviewedBy(
                    workspace = "my-workspace",
                    repoSlug = "other-repo",
                    reviewerUuid = "{5b10ac8d}",
                    pageLen = 50,
                    apiToken = "token"
                )

                // THEN
                assertSoftly(result) {
                    this shouldBe ReviewerQueueQueryResult.Listed(
                        pullRequests = listOf(
                            PullRequestSummary(
                                id = 273L,
                                title = "ABC-663 test",
                                authorDisplayName = "John Smith",
                                authorAvatarUrl = null,
                                createdOn = kotlin.time.Instant.parse("2026-09-28T19:28:03.123456+00:00"),
                                updatedOn = kotlin.time.Instant.parse("2026-09-29T19:28:03.123456+00:00"),
                                sourceBranch = "feature",
                                destinationBranch = "main",
                                commentCount = 1,
                                webUrl = "https://bitbucket.org/my-workspace/other-repo/pull-requests/273",
                                state = PullRequestState.Open,
                                isDraft = false
                            )
                        )
                    )
                    requests.single().url.encodedPath shouldBe
                        "/2.0/repositories/my-workspace/other-repo/pullrequests"
                    requests.single().url.encodedQuery shouldBe
                        "q=state%3D%22OPEN%22%20AND%20reviewers.uuid%3D%22%7B5b10ac8d%7D%22%20AND%20author.uuid%21%3D%22%7B5b10ac8d%7D%22&sort=-updated_on&pagelen=50"
                }
            }

        @Test
        fun `listOpenPullRequestsReviewedBy - should return Listed with no summaries on an empty queue`(): Unit =
            runBlocking {
                // GIVEN
                val client = clientReturning(code = 200, body = """{"values":[]}""")

                // WHEN
                val result = client.listOpenPullRequestsReviewedBy(
                    workspace = "my-workspace",
                    repoSlug = "other-repo",
                    reviewerUuid = "{5b10ac8d}",
                    pageLen = 50,
                    apiToken = "token"
                )

                // THEN an empty queue is an answer, not a failure
                result shouldBe ReviewerQueueQueryResult.Listed(emptyList())
            }

        @Test
        fun `listOpenPullRequestsReviewedBy - should return Skipped on 404`(): Unit =
            runBlocking {
                // GIVEN
                val client = clientReturning(code = 404, body = "")

                // WHEN
                val result = client.listOpenPullRequestsReviewedBy(
                    workspace = "my-workspace",
                    repoSlug = "other-repo",
                    reviewerUuid = "{5b10ac8d}",
                    pageLen = 50,
                    apiToken = "token"
                )

                // THEN
                result shouldBe ReviewerQueueQueryResult.Skipped
            }

        @Test
        fun `listOpenPullRequestsReviewedBy - should return Skipped on 403`(): Unit =
            runBlocking {
                // GIVEN
                val client = clientReturning(code = 403, body = "")

                // WHEN
                val result = client.listOpenPullRequestsReviewedBy(
                    workspace = "my-workspace",
                    repoSlug = "other-repo",
                    reviewerUuid = "{5b10ac8d}",
                    pageLen = 50,
                    apiToken = "token"
                )

                // THEN
                result shouldBe ReviewerQueueQueryResult.Skipped
            }

        @Test
        fun `listOpenPullRequestsReviewedBy - should return Unauthorized on 401`(): Unit =
            runBlocking {
                // GIVEN
                val client = clientReturning(code = 401, body = "")

                // WHEN
                val result = client.listOpenPullRequestsReviewedBy(
                    workspace = "my-workspace",
                    repoSlug = "other-repo",
                    reviewerUuid = "{5b10ac8d}",
                    pageLen = 50,
                    apiToken = "token"
                )

                // THEN
                result shouldBe ReviewerQueueQueryResult.Unauthorized
            }

        @Test
        fun `listOpenPullRequestsReviewedBy - should return Failed on 500`(): Unit =
            runBlocking {
                // GIVEN
                val client = clientReturning(code = 500, body = "")

                // WHEN
                val result = client.listOpenPullRequestsReviewedBy(
                    workspace = "my-workspace",
                    repoSlug = "other-repo",
                    reviewerUuid = "{5b10ac8d}",
                    pageLen = 50,
                    apiToken = "token"
                )

                // THEN
                result shouldBe ReviewerQueueQueryResult.Failed()
            }

        @Test
        fun `listOpenPullRequestsReviewedBy - should return Failed on an unparsable body`(): Unit =
            runBlocking {
                // GIVEN
                val client = clientReturning(code = 200, body = "not json")

                // WHEN
                val result = client.listOpenPullRequestsReviewedBy(
                    workspace = "my-workspace",
                    repoSlug = "other-repo",
                    reviewerUuid = "{5b10ac8d}",
                    pageLen = 50,
                    apiToken = "token"
                )

                // THEN
                result shouldBe ReviewerQueueQueryResult.Failed()
            }

        @Test
        fun `listOpenPullRequestsReviewedBy - should send the Authorization Bearer token`(): Unit =
            runBlocking {
                // GIVEN
                val requests = mutableListOf<Request>()
                val client = clientReturning(code = 200, body = """{"values":[]}""", captured = requests)

                // WHEN
                client.listOpenPullRequestsReviewedBy(
                    workspace = "my-workspace",
                    repoSlug = "other-repo",
                    reviewerUuid = "{5b10ac8d}",
                    pageLen = 50,
                    apiToken = "token"
                )

                // THEN
                requests.single().header("Authorization") shouldBe "Bearer token"
            }
    }

    private fun clientReturning(code: Int, body: String, captured: MutableList<Request> = mutableListOf()): BitbucketPullRequestClient {
        val interceptor = Interceptor { chain ->
            captured.add(chain.request())
            jsonResponse(request = chain.request(), code = code, body = body)
        }
        return OkHttpBitbucketPullRequestClient(OkHttpClient.Builder().addInterceptor(interceptor).build())
    }

    private fun jsonResponse(request: Request, code: Int, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    companion object {
        private val PULL_REQUEST_JSON =
            """
            {
              "id": 273,
              "title": "ABC-663 test",
              "state": "OPEN",
              "comment_count": 1,
              "created_on": "2026-09-28T19:28:03.123456+00:00",
              "updated_on": "2026-09-29T19:28:03.123456+00:00",
              "author": { "display_name": "John Smith" },
              "source": { "branch": { "name": "feature" } },
              "destination": { "branch": { "name": "main" } },
              "links": { "html": { "href": "https://bitbucket.org/my-workspace/other-repo/pull-requests/273" } }
            }
            """.trimIndent()
    }
}