package com.github.fnunezkanut.bbcloud.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The error payload Bitbucket returns from the token endpoint; both fields are safe to log (no credentials). */
data class OAuthErrorBody(val error: String, val description: String?)

/**
 * Parses the Bitbucket Cloud token endpoint JSON. Unknown keys are ignored so the plugin tolerates extra fields.
 */
internal object OAuthTokenResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TokenSuccessPayload(
        val access_token: String? = null,
        val refresh_token: String? = null,
        val expires_in: Long? = null,
        val token_type: String? = null,
        val scopes: String? = null
    )

    @Serializable
    private data class TokenErrorPayload(val error: String? = null, val error_description: String? = null)

    /**
     * Parses a 200 success body into [OAuthTokens], computing [OAuthTokens.expiresAt] from [now] plus `expires_in`
     * (or the default lifetime when absent). Returns null when the access token is blank, or when neither the body nor
     * [previousRefreshToken] supplies a refresh token (a refresh response may omit it, meaning "keep the old one").
     */
    fun parseSuccess(body: String, now: Instant, previousRefreshToken: String?): OAuthTokens? {
        val payload = runCatching { json.decodeFromString<TokenSuccessPayload>(body) }.getOrNull() ?: return null
        val accessToken = payload.access_token?.takeIf { it.isNotBlank() } ?: return null
        val refreshToken = payload.refresh_token?.takeIf { it.isNotBlank() }
            ?: previousRefreshToken?.takeIf { it.isNotBlank() }
            ?: return null
        val lifetime = payload.expires_in?.seconds ?: BbCloudOAuthConfig.DEFAULT_ACCESS_TOKEN_LIFETIME
        return OAuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = now + lifetime,
            scopes = payload.scopes
        )
    }

    /** Parses an error body into [OAuthErrorBody], or null when the body is not parseable or carries no `error`. */
    fun parseError(body: String): OAuthErrorBody? {
        val payload = runCatching { json.decodeFromString<TokenErrorPayload>(body) }.getOrNull() ?: return null
        val error = payload.error?.takeIf { it.isNotBlank() } ?: return null
        return OAuthErrorBody(error = error, description = payload.error_description)
    }
}