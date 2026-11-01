package com.github.fnunezkanut.bbcloud.bitbucket

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Decides which URLs may receive the Bitbucket API token. Pagination `next` links come from response bodies, so they
 * are checked here before the `Authorization` header is attached: only HTTPS on the default port with an exact host
 * match on the Bitbucket API host is trusted.
 *
 * The OAuth token endpoint lives on a different host (`bitbucket.org`, not `api.bitbucket.org`), so [isTrustedOAuthUrl]
 * is a separate gate: it trusts only HTTPS on the default port with host exactly `bitbucket.org` and a path under
 * `/site/oauth2/`. The token endpoint receives the PKCE verifier and refresh token, so it must be guarded too.
 */
internal object BitbucketApiUrlPolicy {
    private const val TRUSTED_SCHEME = "https"
    private const val TRUSTED_HOST = "api.bitbucket.org"
    private const val TRUSTED_PORT = 443

    private const val TRUSTED_OAUTH_HOST = "bitbucket.org"
    private const val TRUSTED_OAUTH_PATH_PREFIX = "/site/oauth2/"

    fun isTrustedApiUrl(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.scheme == TRUSTED_SCHEME &&
            parsed.host == TRUSTED_HOST &&
            parsed.port == TRUSTED_PORT
    }

    /**
     * Whether [url] is the Bitbucket Cloud OAuth endpoint host: HTTPS on port 443, host exactly `bitbucket.org`, and a
     * path under `/site/oauth2/`. Checked before the PKCE verifier or refresh token is posted.
     */
    fun isTrustedOAuthUrl(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        return parsed.scheme == TRUSTED_SCHEME &&
            parsed.host == TRUSTED_OAUTH_HOST &&
            parsed.port == TRUSTED_PORT &&
            parsed.encodedPath.startsWith(TRUSTED_OAUTH_PATH_PREFIX)
    }

    /** Describes a rejected URL by scheme and host only, for logging; never includes the token. */
    fun describeRejected(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return "malformed URL"
        return "scheme=${parsed.scheme}, host=${parsed.host}, port=${parsed.port}"
    }
}