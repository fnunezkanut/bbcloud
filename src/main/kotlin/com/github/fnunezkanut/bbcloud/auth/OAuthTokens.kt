package com.github.fnunezkanut.bbcloud.auth

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A stored OAuth token bundle: the short-lived access token, the long-lived refresh token, the access token's expiry,
 * and the granted scopes (informational). Persisted as a single JSON string so the tokens and expiry are written
 * atomically. [toString] redacts both tokens.
 */
@Serializable
data class OAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    // kotlinx-serialization 1.9.0 ships a built-in serializer for kotlin.time.Instant that encodes it as an ISO-8601
    // string, so no custom serializer is declared here.
    val expiresAt: Instant,
    val scopes: String? = null
) {
    /** Whether the access token is still usable at [now] with [margin] of headroom before expiry. */
    fun isAccessTokenUsable(now: Instant, margin: Duration): Boolean {
        return now < expiresAt - margin
    }

    override fun toString(): String {
        return "OAuthTokens(accessToken=***, refreshToken=***, expiresAt=$expiresAt, scopes=$scopes)"
    }
}