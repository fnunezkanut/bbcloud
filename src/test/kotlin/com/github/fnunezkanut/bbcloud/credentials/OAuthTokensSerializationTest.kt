package com.github.fnunezkanut.bbcloud.credentials

import com.github.fnunezkanut.bbcloud.auth.OAuthTokens
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class OAuthTokensSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `OAuthTokens - should round trip through JSON`() {
        // GIVEN
        val tokens = OAuthTokens(
            accessToken = "at",
            refreshToken = "rt",
            expiresAt = Instant.parse("2026-01-01T12:00:00Z"),
            scopes = "account repository"
        )

        // WHEN
        val encoded = json.encodeToString(tokens)
        val decoded = json.decodeFromString<OAuthTokens>(encoded)

        // THEN
        assertSoftly {
            // The instant is serialized as an ISO-8601 string.
            encoded shouldContain "2026-01-01T12:00:00Z"
            decoded shouldBe tokens
        }
    }

    @Test
    fun `OAuthTokens - decoding corrupt JSON should fail`() {
        // GIVEN a body missing the required accessToken field
        val corrupt = """{"refreshToken":"rt"}"""

        // WHEN
        val result = runCatching { json.decodeFromString<OAuthTokens>(corrupt) }

        // THEN
        result.isFailure shouldBe true
    }
}