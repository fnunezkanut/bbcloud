package com.github.fnunezkanut.bbcloud.auth

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class OAuthTokenResponseParserTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `parseSuccess - should parse a full body`() {
        // GIVEN
        val body =
            """
            {"access_token":"at","refresh_token":"rt","expires_in":7200,"token_type":"bearer","scopes":"account"}
            """.trimIndent()

        // WHEN
        val tokens = OAuthTokenResponseParser.parseSuccess(body = body, now = now, previousRefreshToken = null)

        // THEN
        tokens.shouldNotBeNull()
        assertSoftly {
            tokens.accessToken shouldBe "at"
            tokens.refreshToken shouldBe "rt"
            tokens.expiresAt shouldBe now + kotlin.time.Duration.parse("2h")
            tokens.scopes shouldBe "account"
        }
    }

    @Test
    fun `parseSuccess - should default to the 2 hour lifetime when expires_in is missing`() {
        // GIVEN
        val body = """{"access_token":"at","refresh_token":"rt"}"""

        // WHEN
        val tokens = OAuthTokenResponseParser.parseSuccess(body = body, now = now, previousRefreshToken = null)

        // THEN
        tokens.shouldNotBeNull()
        tokens.expiresAt shouldBe now + kotlin.time.Duration.parse("2h")
    }

    @Test
    fun `parseSuccess - should keep the previous refresh token when the body omits it`() {
        // GIVEN
        val body = """{"access_token":"at","expires_in":7200}"""

        // WHEN
        val tokens = OAuthTokenResponseParser.parseSuccess(body = body, now = now, previousRefreshToken = "old-rt")

        // THEN
        tokens.shouldNotBeNull()
        tokens.refreshToken shouldBe "old-rt"
    }

    @Test
    fun `parseSuccess - should rotate the refresh token when a new one is provided`() {
        // GIVEN
        val body = """{"access_token":"at","refresh_token":"new-rt","expires_in":7200}"""

        // WHEN
        val tokens = OAuthTokenResponseParser.parseSuccess(body = body, now = now, previousRefreshToken = "old-rt")

        // THEN
        tokens.shouldNotBeNull()
        tokens.refreshToken shouldBe "new-rt"
    }

    @Test
    fun `parseSuccess - should return null when there is no refresh token at all`() {
        // GIVEN
        val body = """{"access_token":"at","expires_in":7200}"""

        // WHEN
        val tokens = OAuthTokenResponseParser.parseSuccess(body = body, now = now, previousRefreshToken = null)

        // THEN
        tokens.shouldBeNull()
    }

    @Test
    fun `parseSuccess - should return null when the access token is blank`() {
        // GIVEN
        val body = """{"access_token":"","refresh_token":"rt","expires_in":7200}"""

        // WHEN
        val tokens = OAuthTokenResponseParser.parseSuccess(body = body, now = now, previousRefreshToken = null)

        // THEN
        tokens.shouldBeNull()
    }

    @Test
    fun `parseError - should parse the error and description`() {
        // GIVEN
        val body = """{"error":"invalid_grant","error_description":"code expired"}"""

        // WHEN
        val error = OAuthTokenResponseParser.parseError(body = body)

        // THEN
        error.shouldNotBeNull()
        assertSoftly {
            error.error shouldBe "invalid_grant"
            error.description shouldBe "code expired"
        }
    }

    @Test
    fun `parseError - should return null when there is no error field`() {
        // WHEN
        val error = OAuthTokenResponseParser.parseError(body = """{"foo":"bar"}""")

        // THEN
        error.shouldBeNull()
    }
}