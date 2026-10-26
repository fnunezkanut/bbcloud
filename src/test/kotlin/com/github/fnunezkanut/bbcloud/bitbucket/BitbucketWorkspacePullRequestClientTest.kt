package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BitbucketWorkspacePullRequestClientTest {
    @Test
    fun `listOpenAuthoredPullRequestsInWorkspace - should ask the workspace route for the open pull requests of the user`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = clientReturning(code = 200, body = """{"values":[$PULL_REQUEST_JSON]}""", captured = requests)

            // WHEN
            val result = client.listOpenAuthoredPullRequestsInWorkspace(
                workspace = "my-workspace",
                selectedUserUuid = "{5b10ac8d}",
                apiToken = "token"
            )

            // THEN
            val page = result.shouldNotBeNull()
            assertSoftly(page) {
                entries.size shouldBe 1
                entries.single().repoSlug shouldBe "other-repo"
                isTruncated shouldBe false
                requests.single().url.encodedPath shouldBe "/2.0/workspaces/my-workspace/pullrequests/%7B5b10ac8d%7D"
                requests.single().url.encodedQuery shouldBe "state=OPEN&sort=-updated_on&pagelen=50"
                requests.single().header("Authorization") shouldBe "Bearer token"
            }
        }

    @Test
    fun `listOpenAuthoredPullRequestsInWorkspace - should read an empty listing as no pull requests`(): Unit =
        runBlocking {
            // GIVEN
            val client = clientReturning(code = 200, body = """{"values":[]}""")

            // WHEN
            val result = client.listOpenAuthoredPullRequestsInWorkspace(
                workspace = "my-workspace",
                selectedUserUuid = "{5b10ac8d}",
                apiToken = "token"
            )

            // THEN an empty listing is an answer, not a failure
            result.shouldNotBeNull().entries shouldBe emptyList()
        }

    @Test
    fun `listOpenAuthoredPullRequestsInWorkspace - should return null when the request fails`(): Unit =
        runBlocking {
            // GIVEN
            val client = clientReturning(code = 403, body = "")

            // WHEN
            val result = client.listOpenAuthoredPullRequestsInWorkspace(
                workspace = "my-workspace",
                selectedUserUuid = "{5b10ac8d}",
                apiToken = "token"
            )

            // THEN
            result.shouldBeNull()
        }

    @Test
    fun `listOpenAuthoredPullRequestsInWorkspace - should return null on a body that cannot be parsed`(): Unit =
        runBlocking {
            // GIVEN
            val client = clientReturning(code = 200, body = "not json")

            // WHEN
            val result = client.listOpenAuthoredPullRequestsInWorkspace(
                workspace = "my-workspace",
                selectedUserUuid = "{5b10ac8d}",
                apiToken = "token"
            )

            // THEN
            result.shouldBeNull()
        }

    private fun clientReturning(code: Int, body: String, captured: MutableList<Request> = mutableListOf()): BitbucketPullRequestClient {
        val interceptor = Interceptor { chain ->
            captured.add(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return OkHttpBitbucketPullRequestClient(OkHttpClient.Builder().addInterceptor(interceptor).build())
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
              "links": {
                "self": { "href": "https://api.bitbucket.org/2.0/repositories/my-workspace/other-repo/pullrequests/273" },
                "html": { "href": "https://bitbucket.org/my-workspace/other-repo/pull-requests/273" }
              }
            }
            """.trimIndent()
    }
}