package com.github.fnunezkanut.bbcloud.auth

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class BbCloudBearerRetryAuthenticatorTest {
    private val trustedUrl = "https://api.bitbucket.org/2.0/user"

    @Test
    fun `authenticate - a non-Bearer request returns null`() {
        // GIVEN
        val authenticator = BbCloudBearerRetryAuthenticator(providerLookup = { provider(newToken = "new") })
        val response = response(url = trustedUrl, authorization = "Basic abc")

        // WHEN / THEN
        authenticator.authenticate(route = null, response = response).shouldBeNull()
    }

    @Test
    fun `authenticate - a priorResponse returns null so it only retries once`() {
        // GIVEN
        val authenticator = BbCloudBearerRetryAuthenticator(providerLookup = { provider(newToken = "new") })
        val prior = response(url = trustedUrl, authorization = "Bearer old")
        val response = response(url = trustedUrl, authorization = "Bearer old", priorResponse = prior)

        // WHEN / THEN
        authenticator.authenticate(route = null, response = response).shouldBeNull()
    }

    @Test
    fun `authenticate - an untrusted host returns null`() {
        // GIVEN
        val authenticator = BbCloudBearerRetryAuthenticator(providerLookup = { provider(newToken = "new") })
        val response = response(url = "https://evil.example/2.0/user", authorization = "Bearer old")

        // WHEN / THEN
        authenticator.authenticate(route = null, response = response).shouldBeNull()
    }

    @Test
    fun `authenticate - a new token produces a request with the refreshed header`() {
        // GIVEN
        val authenticator = BbCloudBearerRetryAuthenticator(providerLookup = { provider(newToken = "new-token") })
        val response = response(url = trustedUrl, authorization = "Bearer old")

        // WHEN
        val retried = authenticator.authenticate(route = null, response = response)

        // THEN
        retried?.header("Authorization") shouldBe "Bearer new-token"
    }

    @Test
    fun `authenticate - a null provider token returns null`() {
        // GIVEN
        val authenticator = BbCloudBearerRetryAuthenticator(providerLookup = { provider(newToken = null) })
        val response = response(url = trustedUrl, authorization = "Bearer old")

        // WHEN / THEN
        authenticator.authenticate(route = null, response = response).shouldBeNull()
    }

    @Test
    fun `authenticate - no application provider returns null`() {
        // GIVEN providerLookup returns null, as in a plain unit test with no application
        val authenticator = BbCloudBearerRetryAuthenticator(providerLookup = { null })
        val response = response(url = trustedUrl, authorization = "Bearer old")

        // WHEN / THEN
        authenticator.authenticate(route = null, response = response).shouldBeNull()
    }

    private fun provider(newToken: String?): BbCloudAccessTokenProvider {
        return mockk {
            coEvery { refreshAfterUnauthorized(any()) } returns newToken
        }
    }

    private fun response(url: String, authorization: String, priorResponse: Response? = null): Response {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authorization)
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .apply { if (priorResponse != null) priorResponse(priorResponse) }
            .build()
    }
}