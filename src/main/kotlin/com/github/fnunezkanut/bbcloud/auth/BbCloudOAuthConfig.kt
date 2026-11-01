package com.github.fnunezkanut.bbcloud.auth

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal object BbCloudOAuthConfig {
    /** Bitbucket Cloud authorize endpoint; the browser is opened here. */
    const val AUTHORIZE_URL = "https://bitbucket.org/site/oauth2/authorize"

    /** Bitbucket Cloud token endpoint; code exchange and refresh POST here. */
    const val TOKEN_URL = "https://bitbucket.org/site/oauth2/access_token"

    /** IPv4 loopback host the local callback server binds to; never a wildcard address. */
    const val CALLBACK_HOST = "127.0.0.1"

    /** Fixed port the local callback server listens on, matching the OAuth consumer callback URL. */
    const val CALLBACK_PORT = 42424

    /** Path that receives the OAuth redirect with the authorization code. */
    const val CALLBACK_PATH = "/bbcloud"

    /** Path of the result page the browser is redirected to, so the code never stays in the address bar. */
    const val RESULT_PATH = "/bbcloud/done"

    /**
     * Exact redirect URI sent on both the authorize and the token request. The OAuth consumer's callback URL must be
     * configured to this value, otherwise Bitbucket rejects the authorize request on its own page.
     */
    const val REDIRECT_URI = "http://127.0.0.1:42424/bbcloud"

    /** How long the whole login waits for the browser callback before giving up. */
    val LOGIN_TIMEOUT: Duration = 5.minutes

    /** How long the result page handler blocks waiting for the token-exchange outcome before showing the pending page. */
    val RESULT_PAGE_WAIT: Duration = 30.seconds

    /** How long the server stays up after the outcome is known, so the browser can load the result page. */
    val RESULT_PAGE_GRACE: Duration = 10.seconds

    /** An access token is refreshed proactively once it is within this margin of expiry. */
    val ACCESS_TOKEN_REFRESH_MARGIN: Duration = 5.minutes

    /** Minimum time between refresh attempts after a transient refresh failure. */
    val REFRESH_BACKOFF: Duration = 1.minutes

    /** Access-token lifetime assumed when the token response omits `expires_in`. */
    val DEFAULT_ACCESS_TOKEN_LIFETIME: Duration = 2.hours

    /** How long an online credential check result is cached before the next online check. */
    val CREDENTIAL_CHECK_TTL: Duration = 5.minutes
}