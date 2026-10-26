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
import okio.Buffer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class OkHttpBitbucketPullRequestClientTest {
    @Test
    fun `performAction decline - sends an empty JSON post to the decline endpoint`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketPullRequestClient(capturingClient(requests))
            val summary = summary()

            // WHEN
            val result = client.performAction(
                workspace = "workspace",
                repoSlug = "repo",
                pullRequest = summary,
                action = PullRequestAction.Decline,
                apiToken = "secret"
            )

            // THEN
            val request = requests.single()
            assertSoftly {
                result shouldBe PullRequestActionResult.Success
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/2.0/repositories/workspace/repo/pullrequests/42/decline"
                request.header("Authorization") shouldBe "Bearer secret"
                request.body?.contentType()?.let { mediaType -> "${mediaType.type}/${mediaType.subtype}" } shouldBe "application/json"
                request.bodyText() shouldBe "{}"
            }
        }

    @Test
    fun `updatePullRequestDescription - sends a title-preserving plain-string description PUT`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketPullRequestClient(capturingClient(requests))

            // WHEN
            val result = client.updatePullRequestDescription(
                workspace = "workspace",
                repoSlug = "repo",
                pullRequestId = 42,
                title = "Keep this title",
                description = "## Edited description",
                apiToken = "secret"
            )

            // THEN
            val request = requests.single()
            assertSoftly {
                result shouldBe PullRequestActionResult.Success
                request.method shouldBe "PUT"
                request.url.encodedPath shouldBe "/2.0/repositories/workspace/repo/pullrequests/42"
                request.header("Authorization") shouldBe "Bearer secret"
                request.bodyText() shouldBe """{"title":"Keep this title","description":"## Edited description"}"""
            }
        }

    @Test
    fun `createComment - sends a top-level raw content body`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketPullRequestClient(capturingClient(requests))

            // WHEN
            client.createComment(
                workspace = "workspace",
                repoSlug = "repo",
                pullRequestId = 42,
                content = "Declined: no longer needed",
                apiToken = "secret",
                parentCommentId = null
            )

            // THEN
            val request = requests.single()
            assertSoftly {
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/2.0/repositories/workspace/repo/pullrequests/42/comments"
                request.bodyText() shouldBe "{\"content\":{\"raw\":\"Declined: no longer needed\"}}"
            }
        }

    @Test
    fun `createComment - sends a path-only inline body for a file-scoped comment`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketPullRequestClient(capturingClient(requests))

            // WHEN
            client.createComment(
                workspace = "workspace",
                repoSlug = "repo",
                pullRequestId = 42,
                content = "Review this file",
                apiToken = "secret",
                parentCommentId = null,
                inlinePath = "src/Foo.kt"
            )

            // THEN
            requests.single().bodyText() shouldBe """{"content":{"raw":"Review this file"},"inline":{"path":"src/Foo.kt"}}"""
        }

    @Test
    fun `createComment - sends an AFTER-side line inline body with inline to`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketPullRequestClient(capturingClient(requests))

            // WHEN
            client.createComment(
                workspace = "workspace",
                repoSlug = "repo",
                pullRequestId = 42,
                content = "Nit here",
                apiToken = "secret",
                parentCommentId = null,
                inlinePath = "src/Foo.kt",
                inlineLine = 42,
                inlineSide = InlineCommentSide.AFTER
            )

            // THEN
            requests.single().bodyText() shouldBe """{"content":{"raw":"Nit here"},"inline":{"path":"src/Foo.kt","to":42}}"""
        }

    @Test
    fun `createComment - sends a BEFORE-side line inline body with inline from`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketPullRequestClient(capturingClient(requests))

            // WHEN
            client.createComment(
                workspace = "workspace",
                repoSlug = "repo",
                pullRequestId = 42,
                content = "Was this removed?",
                apiToken = "secret",
                parentCommentId = null,
                inlinePath = "src/Foo.kt",
                inlineLine = 7,
                inlineSide = InlineCommentSide.BEFORE
            )

            // THEN
            requests.single().bodyText() shouldBe """{"content":{"raw":"Was this removed?"},"inline":{"path":"src/Foo.kt","from":7}}"""
        }

    @Test
    fun `resolveCommentThread - posts to the comment resolve endpoint`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketPullRequestClient(capturingClient(requests))

            // WHEN
            val result = client.resolveCommentThread(
                workspace = "workspace",
                repoSlug = "repo",
                pullRequestId = 42,
                commentId = 17,
                apiToken = "secret"
            )

            // THEN
            val request = requests.single()
            assertSoftly {
                result shouldBe PullRequestActionResult.Success
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/2.0/repositories/workspace/repo/pullrequests/42/comments/17/resolve"
                request.header("Authorization") shouldBe "Bearer secret"
                request.bodyText() shouldBe "{}"
            }
        }

    @Test
    fun `reopenCommentThread - deletes the comment resolve endpoint`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketPullRequestClient(
                OkHttpClient.Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            val request = chain.request()
                            requests += request
                            Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(204)
                                .message("No Content")
                                .body("".toResponseBody(null))
                                .build()
                        }
                    )
                    .build()
            )

            // WHEN
            val result = client.reopenCommentThread(
                workspace = "workspace",
                repoSlug = "repo",
                pullRequestId = 42,
                commentId = 17,
                apiToken = "secret"
            )

            // THEN
            val request = requests.single()
            assertSoftly {
                result shouldBe PullRequestActionResult.Success
                request.method shouldBe "DELETE"
                request.url.encodedPath shouldBe "/2.0/repositories/workspace/repo/pullrequests/42/comments/17/resolve"
                request.header("Authorization") shouldBe "Bearer secret"
            }
        }

    @Test
    fun `listAllOpenPullRequests - should follow next pages and concatenate summaries`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketPullRequestClient(
                respondingClient(requests) { request ->
                    when (request.url.queryParameter("page")) {
                        null -> 200 to pageJson(id = 1, next = "https://api.bitbucket.org/2.0/page?page=2")
                        "2" -> 200 to pageJson(id = 2)
                        else -> error("Unexpected page")
                    }
                }
            )

            // WHEN
            val result = client.listAllOpenPullRequests(
                workspace = "workspace",
                repoSlug = "repo",
                apiToken = "secret"
            )

            // THEN
            val success = result as PullRequestListResult.Success
            success.pullRequests.map { summary -> summary.id } shouldBe listOf(1L, 2L)
            requests.size shouldBe 2
        }

    @Test
    fun `listAllOpenPullRequests - should fail when a later page fails`(): Unit =
        runBlocking {
            // GIVEN
            val client = OkHttpBitbucketPullRequestClient(
                respondingClient(mutableListOf()) { request ->
                    if (request.url.queryParameter("page") == null) {
                        200 to pageJson(id = 1, next = "https://api.bitbucket.org/2.0/page?page=2")
                    } else {
                        500 to "{}"
                    }
                }
            )

            // WHEN
            val result = client.listAllOpenPullRequests(
                workspace = "workspace",
                repoSlug = "repo",
                apiToken = "secret"
            )

            // THEN
            result shouldBe PullRequestListResult.Failed()
        }

    @Test
    fun `listAllOpenPullRequests - should fail rather than return a truncated set at the page cap`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketPullRequestClient(
                respondingClient(requests) { request ->
                    val page = request.url.queryParameter("page")?.toIntOrNull() ?: 1
                    200 to pageJson(
                        id = page.toLong(),
                        next = "https://api.bitbucket.org/2.0/page?page=${page + 1}"
                    )
                }
            )

            // WHEN
            val result = client.listAllOpenPullRequests(
                workspace = "workspace",
                repoSlug = "repo",
                apiToken = "secret"
            )

            // THEN
            result shouldBe PullRequestListResult.Failed()
            requests.size shouldBe 40
        }

    @Test
    fun `createPullRequest - posts to the pullrequests endpoint and returns Created on 201`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketPullRequestClient(
                respondingClient(requests) { 201 to pageCreatedJson(id = 99) }
            )

            // WHEN
            val result = client.createPullRequest(
                workspace = "workspace",
                repoSlug = "repo",
                request = com.github.fnunezkanut.bbcloud.bitbucket.NewPullRequest(
                    title = "a title",
                    description = "body",
                    sourceBranch = "feature/x",
                    destinationBranch = "main",
                    reviewerUuids = emptyList(),
                    closeSourceBranch = true,
                    isDraft = false
                ),
                apiToken = "secret"
            )

            // THEN
            val request = requests.single()
            assertSoftly {
                result shouldBe CreatePullRequestResult.Created(summary = summaryWithId(id = 99))
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/2.0/repositories/workspace/repo/pullrequests"
                request.header("Authorization") shouldBe "Bearer secret"
                request.body?.contentType()?.toString() shouldBe "application/json; charset=utf-8"
            }
        }

    @Test
    fun `createPullRequest - returns the Bitbucket error message on a 400`(): Unit =
        runBlocking {
            // GIVEN
            val client = OkHttpBitbucketPullRequestClient(
                respondingClient(mutableListOf()) {
                    400 to """{"type":"error","error":{"message":"There are no changes to be pulled"}}"""
                }
            )

            // WHEN
            val result = client.createPullRequest(
                workspace = "workspace",
                repoSlug = "repo",
                request = com.github.fnunezkanut.bbcloud.bitbucket.NewPullRequest(
                    title = "a title",
                    description = "",
                    sourceBranch = "feature/x",
                    destinationBranch = "main",
                    reviewerUuids = emptyList(),
                    closeSourceBranch = true,
                    isDraft = false
                ),
                apiToken = "secret"
            )

            // THEN
            result shouldBe CreatePullRequestResult.Failed(message = "There are no changes to be pulled")
        }

    @Test
    fun `findOpenPullRequest - returns None for an empty values page`(): Unit =
        runBlocking {
            // GIVEN
            val client = OkHttpBitbucketPullRequestClient(respondingClient(mutableListOf()) { 200 to """{"values":[]}""" })

            // WHEN
            val result = client.findOpenPullRequest(
                workspace = "workspace",
                repoSlug = "repo",
                sourceBranch = "feature/x",
                destinationBranch = "main",
                apiToken = "secret"
            )

            // THEN
            result shouldBe ExistingPullRequestLookupResult.None
        }

    @Test
    fun `findOpenPullRequest - returns Found for one value`(): Unit =
        runBlocking {
            // GIVEN
            val client = OkHttpBitbucketPullRequestClient(respondingClient(mutableListOf()) { 200 to pageJson(id = 7) })

            // WHEN
            val result = client.findOpenPullRequest(
                workspace = "workspace",
                repoSlug = "repo",
                sourceBranch = "feature/7",
                destinationBranch = "main",
                apiToken = "secret"
            )

            // THEN
            val found = result as ExistingPullRequestLookupResult.Found
            found.summary.id shouldBe 7L
        }

    @Test
    fun `findOpenPullRequest - returns Failed on a 500`(): Unit =
        runBlocking {
            // GIVEN
            val client = OkHttpBitbucketPullRequestClient(respondingClient(mutableListOf()) { 500 to "{}" })

            // WHEN
            val result = client.findOpenPullRequest(
                workspace = "workspace",
                repoSlug = "repo",
                sourceBranch = "feature/x",
                destinationBranch = "main",
                apiToken = "secret"
            )

            // THEN a failed fetch is a failed lookup
            (result is ExistingPullRequestLookupResult.Failed) shouldBe true
        }

    @Test
    fun `listBranches - follows next and is not truncated when there is no next`(): Unit =
        runBlocking {
            // GIVEN
            val client = OkHttpBitbucketPullRequestClient(
                respondingClient(mutableListOf()) { request ->
                    when (request.url.queryParameter("page")) {
                        null -> 200 to """{"values":[{"name":"main"}],"next":"https://api.bitbucket.org/2.0/b?page=2"}"""
                        else -> 200 to """{"values":[{"name":"feature/x"}]}"""
                    }
                }
            )

            // WHEN
            val result = client.listBranches(workspace = "workspace", repoSlug = "repo", apiToken = "secret")

            // THEN
            assertSoftly {
                result?.names shouldBe listOf("main", "feature/x")
                result?.isTruncated shouldBe false
            }
        }

    @Test
    fun `getMainBranchName - reads the mainbranch name`(): Unit =
        runBlocking {
            // GIVEN
            val client = OkHttpBitbucketPullRequestClient(respondingClient(mutableListOf()) { 200 to """{"mainbranch":{"name":"develop"}}""" })

            // WHEN
            val result = client.getMainBranchName(workspace = "workspace", repoSlug = "repo", apiToken = "secret")

            // THEN
            result shouldBe "develop"
        }

    private fun pageCreatedJson(id: Long): String {
        return """
            {
              "id": $id,
              "title": "PR $id",
              "state": "OPEN",
              "updated_on": "2026-09-30T12:00:00Z",
              "author": { "display_name": "Author" },
              "source": { "branch": { "name": "feature/$id" } },
              "destination": { "branch": { "name": "main" } },
              "links": { "html": { "href": "https://bitbucket.org/workspace/repo/pull-requests/$id" } }
            }
            """.trimIndent()
    }

    private fun summaryWithId(id: Long): PullRequestSummary {
        val instant = Instant.parse("2026-09-30T12:00:00Z")
        return PullRequestSummary(
            id = id,
            title = "PR $id",
            authorDisplayName = "Author",
            authorAvatarUrl = null,
            createdOn = instant,
            updatedOn = instant,
            sourceBranch = "feature/$id",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/workspace/repo/pull-requests/$id",
            state = PullRequestState.Open,
            isDraft = false
        )
    }

    private fun respondingClient(requests: MutableList<Request>, responseFor: (Request) -> Pair<Int, String>): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val (code, body) = responseFor(request)
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code == 200) "OK" else "ERROR")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
            )
            .build()
    }

    private fun pageJson(id: Long, next: String? = null): String {
        val nextField = next?.let { value -> ",\"next\":\"$value\"" }.orEmpty()
        return """
            {
              "values": [
                {
                  "id": $id,
                  "title": "PR $id",
                  "state": "OPEN",
                  "updated_on": "2026-09-30T12:00:00Z",
                  "author": { "display_name": "Author" },
                  "source": { "branch": { "name": "feature/$id" } },
                  "destination": { "branch": { "name": "main" } },
                  "links": { "html": { "href": "https://bitbucket.org/workspace/repo/pull-requests/$id" } }
                }
              ]$nextField
            }
            """.trimIndent()
    }

    private fun capturingClient(requests: MutableList<Request>): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    requests += request
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            )
            .build()
    }

    private fun Request.bodyText(): String {
        val buffer = Buffer()
        body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun summary(): PullRequestSummary {
        val instant = Instant.parse("2026-09-30T12:00:00Z")
        return PullRequestSummary(
            id = 42,
            title = "PR 42",
            authorDisplayName = "Author",
            authorAvatarUrl = null,
            createdOn = instant,
            updatedOn = instant,
            sourceBranch = "feature/42",
            destinationBranch = "main",
            commentCount = 0,
            webUrl = "https://bitbucket.org/workspace/repo/pull-requests/42",
            state = PullRequestState.Open,
            isDraft = false
        )
    }
}