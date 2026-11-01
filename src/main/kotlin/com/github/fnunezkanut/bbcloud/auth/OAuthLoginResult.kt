package com.github.fnunezkanut.bbcloud.auth

/**
 * The terminal outcome of an OAuth login. Cancellation is not represented here; it propagates as a
 * [kotlinx.coroutines.CancellationException].
 */
sealed class OAuthLoginResult {
    data class Success(val tokens: OAuthTokens) : OAuthLoginResult()

    /** The local callback port could not be bound; the browser was not opened. */
    data object PortUnavailable : OAuthLoginResult()

    /** The browser could not be launched and no callback arrived before the timeout. */
    data class BrowserLaunchFailed(val authorizeUrl: String) : OAuthLoginResult()

    /** No callback arrived before the login timeout. */
    data object TimedOut : OAuthLoginResult()

    /** The user denied authorization. */
    data class Denied(val description: String?) : OAuthLoginResult()

    /** The callback carried neither a code nor an error. */
    data object InvalidCallback : OAuthLoginResult()

    data class ClientRejected(val description: String?) : OAuthLoginResult()

    /** The token exchange failed for a transient or permanent reason. */
    data class ExchangeFailed(val cause: Throwable? = null) : OAuthLoginResult()
}