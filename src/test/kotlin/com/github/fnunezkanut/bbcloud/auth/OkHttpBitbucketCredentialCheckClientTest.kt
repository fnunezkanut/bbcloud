package com.github.fnunezkanut.bbcloud.auth

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
class OkHttpBitbucketCredentialCheckClientTest {
    @Test
    fun `check - 200 should be Valid and send the Bearer header`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketCredentialCheckClient(httpClient = respondingClient(requests) { 200 })

            // WHEN
            val result = client.check(bearerToken = "token-value")

            // THEN
            result.shouldBeInstanceOf<CredentialCheckResult.Valid>()
            requests.single().header("Authorization") shouldBe "Bearer token-value"
        }

    @Test
    fun `check - 401 should be Unauthorized`(): Unit =
        runBlocking {
            val client = OkHttpBitbucketCredentialCheckClient(httpClient = respondingClient(mutableListOf()) { 401 })
            client.check(bearerToken = "t").shouldBeInstanceOf<CredentialCheckResult.Unauthorized>()
        }

    @Test
    fun `check - 403 should be Forbidden`(): Unit =
        runBlocking {
            val client = OkHttpBitbucketCredentialCheckClient(httpClient = respondingClient(mutableListOf()) { 403 })
            client.check(bearerToken = "t").shouldBeInstanceOf<CredentialCheckResult.Forbidden>()
        }

    @Test
    fun `check - 500 should be Unavailable`(): Unit =
        runBlocking {
            val client = OkHttpBitbucketCredentialCheckClient(httpClient = respondingClient(mutableListOf()) { 500 })
            client.check(bearerToken = "t").shouldBeInstanceOf<CredentialCheckResult.Unavailable>()
        }

    @Test
    fun `check - 429 should be Unavailable`(): Unit =
        runBlocking {
            val client = OkHttpBitbucketCredentialCheckClient(httpClient = respondingClient(mutableListOf()) { 429 })
            client.check(bearerToken = "t").shouldBeInstanceOf<CredentialCheckResult.Unavailable>()
        }

    @Test
    fun `check - IOException should be Unavailable`(): Unit =
        runBlocking {
            val client = OkHttpBitbucketCredentialCheckClient(
                httpClient = OkHttpClient.Builder()
                    .addInterceptor(Interceptor { throw IOException("offline") })
                    .build()
            )
            client.check(bearerToken = "t").shouldBeInstanceOf<CredentialCheckResult.Unavailable>()
        }

    private fun respondingClient(requests: MutableList<Request>, codeFor: (Request) -> Int): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    requests += request
                    val code = codeFor(request)
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(code)
                        .message(if (code == 200) "OK" else "ERROR")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            )
            .build()
    }
}