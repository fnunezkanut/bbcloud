package com.github.fnunezkanut.bbcloud.credentials

import com.github.fnunezkanut.bbcloud.auth.OAuthTokens

/**
 * Stores the plugin's Bitbucket credentials in the IDE's secure storage. The API token and the OAuth token bundle are
 * independent entries, and both may exist at once (OAuth is preferred, the API token is the fallback). Removing one
 * leaves the other untouched.
 */
interface BbCloudCredentialStore {
    suspend fun hasToken(): Boolean

    suspend fun getToken(): String?

    suspend fun saveToken(token: String)

    suspend fun clearToken()

    /** The stored OAuth token bundle, or null when none is stored or the stored entry is corrupt. */
    suspend fun getOAuthTokens(): OAuthTokens?

    /** Writes the OAuth token bundle atomically as a single JSON entry. */
    suspend fun saveOAuthTokens(tokens: OAuthTokens)

    /** Removes the stored OAuth token bundle; the API token entry is left untouched. */
    suspend fun clearOAuthTokens()
}