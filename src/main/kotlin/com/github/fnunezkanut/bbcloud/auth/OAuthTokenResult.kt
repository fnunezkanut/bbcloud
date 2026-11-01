package com.github.fnunezkanut.bbcloud.auth

/** The outcome of a token-endpoint call (code exchange or refresh). */
sealed class OAuthTokenResult {
    data class Success(val tokens: OAuthTokens) : OAuthTokenResult()

    /** Permanent: the authorization code or refresh token was rejected (`invalid_grant`). */
    data class InvalidGrant(val description: String?) : OAuthTokenResult()

    data class InvalidClient(val error: String, val description: String?) : OAuthTokenResult()

    /** Transient or unknown failure: I/O error, 5xx, or an unrecognized error code. */
    data class Failed(val cause: Throwable? = null, val error: String? = null, val description: String? = null) : OAuthTokenResult()
}