package com.github.fnunezkanut.bbcloud.auth

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketApiUrlPolicy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * OkHttp [Authenticator] that retries a Bitbucket API request once after a 401, using a token refreshed by the
 * [BbCloudAccessTokenProvider]. It only acts on trusted Bitbucket API URLs that carried a `Bearer` header and that have
 * not already been retried. [providerLookup] returns null when no application is present (plain unit tests), so no
 * retry happens there.
 */
internal class BbCloudBearerRetryAuthenticator(
    private val providerLookup: () -> BbCloudAccessTokenProvider? = {
        ApplicationManager.getApplication()?.serviceOrNull<BbCloudAccessTokenProvider>()
    }
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val authorization = response.request.header("Authorization")
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null
        }
        // Retry at most once: a priorResponse means this request already came from an authenticator retry.
        if (response.priorResponse != null) {
            return null
        }
        val url = response.request.url.toString()
        if (!BitbucketApiUrlPolicy.isTrustedApiUrl(url)) {
            return null
        }
        val provider = providerLookup() ?: return null
        val rejectedToken = authorization.removePrefix(BEARER_PREFIX)
        val newToken = runBlocking { provider.refreshAfterUnauthorized(rejectedToken = rejectedToken) }
        val retried = newToken != null
        LOG.debug("401 for ${response.request.method} $url; retrying with refreshed credentials: $retried")
        if (newToken == null) {
            return null
        }
        return response.request.newBuilder()
            .header("Authorization", "$BEARER_PREFIX$newToken")
            .build()
    }

    companion object {
        private val LOG = logger<BbCloudBearerRetryAuthenticator>()

        private const val BEARER_PREFIX = "Bearer "
    }
}