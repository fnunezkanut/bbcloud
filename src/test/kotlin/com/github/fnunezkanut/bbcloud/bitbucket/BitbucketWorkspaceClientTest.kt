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
class BitbucketWorkspaceClientTest {
    @Test
    fun `listWorkspaces - should call the user workspaces endpoint with a bearer token`(): Unit =
        runBlocking {
            val requests = mutableListOf<Request>()
            val client = clientReturning(
                code = 200,
                body = """{"values":[{"administrator":true,"type":"workspace_access","workspace":{"slug":"my-workspace"}}]}""",
                captured = requests
            )

            client.listWorkspaces(apiToken = "token")

            assertSoftly(requests.single()) {
                url.encodedPath shouldBe "/2.0/user/workspaces"
                header("Authorization") shouldBe "Bearer token"
            }
        }

    @Test
    fun `listWorkspaces - should return workspaces on 200`(): Unit =
        runBlocking {
            val body = """{"values":[{"administrator":true,"type":"workspace_access","workspace":{"slug":"my-workspace","uuid":"{ws-1}"}}]}"""
            val client = clientReturning(code = 200, body = body)

            val result = client.listWorkspaces(apiToken = "token")

            result.shouldBeInstanceOf<WorkspaceListResult.Success>()
            result.shouldBeInstanceOf<WorkspaceListResult.Success>().workspaces shouldBe listOf(
                BitbucketWorkspace(slug = "my-workspace", uuid = "{ws-1}")
            )
        }

    @Test
    fun `listWorkspaces - should return unauthorized on 401 and 403`(): Unit =
        runBlocking {
            listOf(401, 403).forEach { code ->
                clientReturning(code = code, body = "").listWorkspaces(apiToken = "token") shouldBe WorkspaceListResult.Unauthorized
            }
        }

    @Test
    fun `listWorkspaces - should fail on an unexpected status`(): Unit =
        runBlocking {
            val result = clientReturning(code = 500, body = "").listWorkspaces(apiToken = "token")

            result.shouldBeInstanceOf<WorkspaceListResult.Failed>()
        }

    @Test
    fun `listWorkspaces - should fail on an io error`(): Unit =
        runBlocking {
            val client = OkHttpBitbucketWorkspaceClient(
                OkHttpClient.Builder()
                    .addInterceptor { _ -> throw IOException("boom") }
                    .build()
            )

            val result = client.listWorkspaces(apiToken = "token")

            result.shouldBeInstanceOf<WorkspaceListResult.Failed>()
            result.shouldBeInstanceOf<WorkspaceListResult.Failed>().cause.shouldBeInstanceOf<IOException>()
        }

    private fun clientReturning(code: Int, body: String, captured: MutableList<Request> = mutableListOf()): BitbucketWorkspaceClient {
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
        return OkHttpBitbucketWorkspaceClient(OkHttpClient.Builder().addInterceptor(interceptor).build())
    }
}