package com.github.fnunezkanut.bbcloud.auth

/**
 * Supplies the bearer token for Bitbucket API calls: a working OAuth access token when available, otherwise the stored
 * API token. Also drives the proactive refresh and the single forced refresh after a 401.
 */
interface BbCloudAccessTokenProvider {
    /** Bearer token for Bitbucket API calls: a working OAuth access token, else the API token, else null. */
    suspend fun bearerTokenOrNull(): String?

    /** A usable (proactively refreshed) OAuth access token, or null when OAuth is not stored or not working. */
    suspend fun oauthAccessTokenOrNull(): String?

    /**
     * Called after Bitbucket answered 401 for [rejectedToken]. Returns a different token to retry with once (a
     * refreshed OAuth token, or the API token fallback), or null to give up.
     */
    suspend fun refreshAfterUnauthorized(rejectedToken: String): String?

    /** Marks the stored OAuth tokens as not working until new OAuth tokens are saved (in memory only). */
    fun markOAuthNotWorking()

    /** Clears the in-memory "not working" flag; called when new OAuth tokens are saved. */
    fun onOAuthTokensChanged()
}