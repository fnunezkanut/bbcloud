package com.github.fnunezkanut.bbcloud.auth

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
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
import java.io.IOException

@Tag("unit")
class OkHttpBitbucketOAuthTokenClientTest {
    @Test
    fun `exchangeCode - should post the authorization_code form with redirect_uri and code_verifier`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketOAuthTokenClient(
                httpClient = respondingClient(requests) { 200 to successBody() }
            )

            // WHEN
            client.exchangeCode(code = "the-code", codeVerifier = "the-verifier")

            // THEN
            val form = requests.single().formFields()
            assertSoftly {
                form["grant_type"] shouldBe "authorization_code"
                form["code"] shouldBe "the-code"
                form["code_verifier"] shouldBe "the-verifier"
                form["redirect_uri"] shouldBe "http://127.0.0.1:42424/bbcloud"
                form[BbCloudConsumerMaterial.primaryField()] shouldBe BbCloudConsumerMaterial.primary()
            }
        }

    @Test
    fun `exchangeCode - should send HTTP Basic authorization`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketOAuthTokenClient(
                httpClient = respondingClient(requests) { 200 to successBody() }
            )

            // WHEN
            client.exchangeCode(code = "c", codeVerifier = "v")

            // THEN
            val header = requests.single().header("Authorization")
            assertSoftly {
                header shouldBe Credentials.basic(
                    username = BbCloudConsumerMaterial.primary(),
                    password = BbCloudConsumerMaterial.secondary(),
                    charset = Charsets.UTF_8
                )
                header?.startsWith("Basic ") shouldBe true
            }
        }

    @Test
    fun `refresh - should send HTTP Basic authorization`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketOAuthTokenClient(
                httpClient = respondingClient(requests) { 200 to successBody() }
            )

            // WHEN
            client.refresh(refreshToken = "old-rt")

            // THEN
            val header = requests.single().header("Authorization")
            assertSoftly {
                header shouldBe Credentials.basic(
                    username = BbCloudConsumerMaterial.primary(),
                    password = BbCloudConsumerMaterial.secondary(),
                    charset = Charsets.UTF_8
                )
                header?.startsWith("Basic ") shouldBe true
            }
        }

    @Test
    fun `exchangeCode - should keep the form free of the secondary value`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketOAuthTokenClient(
                httpClient = respondingClient(requests) { 200 to successBody() }
            )

            // WHEN
            client.exchangeCode(code = "the-code", codeVerifier = "the-verifier")

            // THEN
            val form = requests.single().formFields()
            assertSoftly {
                form.keys shouldBe setOf(
                    "grant_type",
                    "code",
                    "code_verifier",
                    "redirect_uri",
                    BbCloudConsumerMaterial.primaryField()
                )
                form.values.contains(BbCloudConsumerMaterial.secondary()) shouldBe false
            }
        }

    @Test
    fun `refresh - should post the refresh_token form`(): Unit =
        runBlocking {
            // GIVEN
            val requests = mutableListOf<Request>()
            val client = OkHttpBitbucketOAuthTokenClient(
                httpClient = respondingClient(requests) { 200 to successBody() }
            )

            // WHEN
            client.refresh(refreshToken = "old-rt")

            // THEN
            val form = requests.single().formFields()
            assertSoftly {
                form["grant_type"] shouldBe "refresh_token"
                form["refresh_token"] shouldBe "old-rt"
                form[BbCloudConsumerMaterial.primaryField()] shouldBe BbCloudConsumerMaterial.primary()
            }
        }

    @Test
    fun `exchangeCode - 400 invalid_grant should map to InvalidGrant`(): Unit =
        runBlocking {
            // GIVEN
            val client = OkHttpBitbucketOAuthTokenClient(
                httpClient = respondingClient(mutableListOf()) {
                    400 to """{"error":"invalid_grant","error_description":"expired"}"""
                }
            )

            // WHEN
            val result = client.exchangeCode(code = "c", codeVerifier = "v")

            // THEN
            result.shouldBeInstanceOf<OAuthTokenResult.InvalidGrant>()
        }

    @Test
    fun `exchangeCode - 401 invalid_client should map to InvalidClient`(): Unit =
        runBlocking {
            // GIVEN
            val client = OkHttpBitbucketOAuthTokenClient(
                httpClient = respondingClient(mutableListOf()) {
                    401 to """{"error":"invalid_client","error_description":"client rejected"}"""
                }
            )

            // WHEN
            val result = client.exchangeCode(code = "c", codeVerifier = "v")

            // THEN
            result.shouldBeInstanceOf<OAuthTokenResult.InvalidClient>()
        }

    @Test
    fun `exchangeCode - 500 should map to Failed`(): Unit =
        runBlocking {
            // GIVEN
            val client = OkHttpBitbucketOAuthTokenClient(
                httpClient = respondingClient(mutableListOf()) { 500 to """{"error":"server_error"}""" }
            )

            // WHEN
            val result = client.exchangeCode(code = "c", codeVerifier = "v")

            // THEN
            result.shouldBeInstanceOf<OAuthTokenResult.Failed>()
        }

    @Test
    fun `exchangeCode - IOException should map to Failed`(): Unit =
        runBlocking {
            // GIVEN a client whose interceptor throws
            val client = OkHttpBitbucketOAuthTokenClient(
                httpClient = OkHttpClient.Builder()
                    .addInterceptor(Interceptor { throw IOException("boom") })
                    .build()
            )

            // WHEN
            val result = client.exchangeCode(code = "c", codeVerifier = "v")

            // THEN
            result.shouldBeInstanceOf<OAuthTokenResult.Failed>()
        }

    private fun successBody(): String {
        return """{"access_token":"at","refresh_token":"rt","expires_in":7200,"token_type":"bearer","scopes":"account"}"""
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

    private fun Request.formFields(): Map<String, String> {
        val buffer = Buffer()
        body?.writeTo(buffer)
        val encoded = buffer.readUtf8()
        return encoded.split("&").associate { pair ->
            val parts = pair.split("=", limit = 2)
            val name = java.net.URLDecoder.decode(parts[0], Charsets.UTF_8)
            val value = java.net.URLDecoder.decode(parts.getOrElse(1) { "" }, Charsets.UTF_8)
            name to value
        }
    }
}