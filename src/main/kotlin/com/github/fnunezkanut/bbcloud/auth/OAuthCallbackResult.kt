package com.github.fnunezkanut.bbcloud.auth

/** The result the local callback server extracted from the OAuth redirect. */
sealed class OAuthCallbackResult {
    /** The authorization code was returned. */
    data class Code(val code: String) : OAuthCallbackResult()

    /** The user denied authorization (`error` present on the redirect). */
    data class Denied(val error: String, val description: String?) : OAuthCallbackResult()

    /** The redirect carried neither a code nor an error, after the state matched. */
    data class Invalid(val reason: String) : OAuthCallbackResult()
}