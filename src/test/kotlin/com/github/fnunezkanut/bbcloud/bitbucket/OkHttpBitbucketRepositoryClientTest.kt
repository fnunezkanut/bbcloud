package com.github.fnunezkanut.bbcloud.bitbucket

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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
import java.io.IOException

@Tag("unit")
class OkHttpBitbucketRepositoryClientTest {
    @Test
    fun `checkRepositoryExists - should return Exists with the avatar href on a 200 that carries one`(): Unit =
        runBlocking {
            // GIVEN
            val body = """{"links":{"avatar":{"href":"https://cdn.example.com/avatar.png"}}}"""
            val client = clientReturning(code = 200, body = body)

            // WHEN
            val result = client.checkRepositoryExists(workspace = "my-workspace", repoSlug = "repo-a", apiToken = "token")

            // THEN
            assertSoftly {
                result.shouldBeInstanceOf<RepositoryCheckResult.Exists>()
                result.shouldBeInstanceOf<RepositoryCheckResult.Exists>().avatarUrl shouldBe "https://cdn.example.com/avatar.png"
            }
        }

    @Test
    fun `checkRepositoryExists - should request only the avatar href field with a bearer token`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = clientReturning(code = 200, body = """{"links":{"avatar":{"href":"https://cdn.example.com/a.png"}}}""", captured = requests)

            // WHEN
            client.checkRepositoryExists(workspace = "my-workspace", repoSlug = "repo-a", apiToken = "token")

            // THEN
            assertSoftly(requests.single()) {
                url.encodedPath shouldBe "/2.0/repositories/my-workspace/repo-a"
                url.queryParameter("fields") shouldBe "links.avatar.href"
                header("Authorization") shouldBe "Bearer token"
            }
        }

    @Test
    fun `checkRepositoryExists - should return Exists with a null avatar when the 200 body has no avatar`(): Unit =
        runBlocking {
            // GIVEN
            val client = clientReturning(code = 200, body = """{"links":{}}""")

            // WHEN
            val result = client.checkRepositoryExists(workspace = "my-workspace", repoSlug = "repo-a", apiToken = "token")

            // THEN
            assertSoftly {
                result.shouldBeInstanceOf<RepositoryCheckResult.Exists>()
                result.shouldBeInstanceOf<RepositoryCheckResult.Exists>().avatarUrl shouldBe null
            }
        }

    @Test
    fun `checkRepositoryExists - should treat a blank avatar href as no avatar`(): Unit =
        runBlocking {
            // GIVEN
            val client = clientReturning(code = 200, body = """{"links":{"avatar":{"href":"   "}}}""")

            // WHEN
            val result = client.checkRepositoryExists(workspace = "my-workspace", repoSlug = "repo-a", apiToken = "token")

            // THEN
            result.shouldBeInstanceOf<RepositoryCheckResult.Exists>().avatarUrl shouldBe null
        }

    @Test
    fun `checkRepositoryExists - should return Exists with a null avatar when the 200 body is malformed, not Failed`(): Unit =
        runBlocking {
            // GIVEN a real 200 whose body is not valid JSON
            val client = clientReturning(code = 200, body = "not-json")

            // WHEN
            val result = client.checkRepositoryExists(workspace = "my-workspace", repoSlug = "repo-a", apiToken = "token")

            // THEN a parse failure must NOT downgrade a real 200 to Failed
            assertSoftly {
                result.shouldBeInstanceOf<RepositoryCheckResult.Exists>()
                result.shouldBeInstanceOf<RepositoryCheckResult.Exists>().avatarUrl shouldBe null
            }
        }

    @Test
    fun `checkRepositoryExists - should return NotFound on 404`(): Unit =
        runBlocking {
            // GIVEN
            val client = clientReturning(code = 404, body = "")

            // WHEN
            val result = client.checkRepositoryExists(workspace = "my-workspace", repoSlug = "repo-a", apiToken = "token")

            // THEN
            result shouldBe RepositoryCheckResult.NotFound
        }

    @Test
    fun `checkRepositoryExists - should return Failed on an unexpected status`(): Unit =
        runBlocking {
            // GIVEN
            val client = clientReturning(code = 500, body = "")

            // WHEN
            val result = client.checkRepositoryExists(workspace = "my-workspace", repoSlug = "repo-a", apiToken = "token")

            // THEN
            result.shouldBeInstanceOf<RepositoryCheckResult.Failed>()
        }

    @Test
    fun `checkRepositoryExists - should return Failed on an io error`(): Unit =
        runBlocking {
            // GIVEN
            val client = OkHttpBitbucketRepositoryClient(
                OkHttpClient.Builder()
                    .addInterceptor { _ -> throw IOException("boom") }
                    .build()
            )

            // WHEN
            val result = client.checkRepositoryExists(workspace = "my-workspace", repoSlug = "repo-a", apiToken = "token")

            // THEN
            assertSoftly {
                result.shouldBeInstanceOf<RepositoryCheckResult.Failed>()
                result.shouldBeInstanceOf<RepositoryCheckResult.Failed>().cause.shouldBeInstanceOf<IOException>()
            }
        }

    private fun clientReturning(code: Int, body: String, captured: MutableList<Request> = mutableListOf()): BitbucketRepositoryClient {
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
        return OkHttpBitbucketRepositoryClient(OkHttpClient.Builder().addInterceptor(interceptor).build())
    }
}