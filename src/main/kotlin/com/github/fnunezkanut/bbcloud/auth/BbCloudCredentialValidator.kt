package com.github.fnunezkanut.bbcloud.auth

import com.github.fnunezkanut.bbcloud.credentials.BbCloudCredentialStore
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Determines which authentication method actually works online, by calling `GET /2.0/user`. The result is cached for
 * [ttl] (a short window when the check could not reach Bitbucket) behind a [Mutex] so concurrent tab switches cause a
 * single check. OAuth is preferred (with one forced refresh on a 401); the API token is the fallback.
 */
class BbCloudCredentialValidator(
    private val credentialStore: BbCloudCredentialStore,
    private val accessTokenProvider: BbCloudAccessTokenProvider,
    private val checkClient: BitbucketCredentialCheckClient,
    private val clock: Clock,
    private val ttl: Duration = BbCloudOAuthConfig.CREDENTIAL_CHECK_TTL
) {
    private val mutex = Mutex()

    private data class CachedResult(val method: BbCloudAuthenticationMethod, val computedAt: Instant, val ttl: Duration)

    @Volatile
    private var cached: CachedResult? = null

    /** The last method confirmed by a definite (not [CredentialCheckResult.Unavailable]) check, for offline fallback. */
    @Volatile
    private var lastKnownWorking: BbCloudAuthenticationMethod? = null

    /** The working authentication method, cached for [ttl]. Concurrent callers share one online check. */
    suspend fun workingMethod(): BbCloudAuthenticationMethod {
        return mutex.withLock {
            val now = clock.now()
            val current = cached
            if (current != null && now < current.computedAt + current.ttl) {
                LOG.debug("Credential check cache hit: ${current.method}")
                return@withLock current.method
            }
            val (method, definite) = computeWorkingMethod()
            if (definite) {
                lastKnownWorking = method
            }
            val entryTtl = if (definite) ttl else UNAVAILABLE_CACHE_TTL
            cached = CachedResult(method = method, computedAt = now, ttl = entryTtl)
            method
        }
    }

    /** Returns the working method and whether the check was definite (true) or an offline fallback (false). */
    private suspend fun computeWorkingMethod(): Pair<BbCloudAuthenticationMethod, Boolean> {
        val oauthTokens = credentialStore.getOAuthTokens()
        if (oauthTokens != null) {
            val oauthOutcome = checkOAuth()
            if (oauthOutcome != null) {
                return oauthOutcome
            }
        }

        val apiToken = credentialStore.getToken()
        if (apiToken != null) {
            return when (val result = checkClient.check(bearerToken = apiToken)) {
                is CredentialCheckResult.Valid -> {
                    methodChange(BbCloudAuthenticationMethod.ApiToken) to true
                }
                is CredentialCheckResult.Unauthorized, is CredentialCheckResult.Forbidden -> {
                    methodChange(BbCloudAuthenticationMethod.None) to true
                }
                is CredentialCheckResult.Unavailable -> {
                    offlineFallback(stored = BbCloudAuthenticationMethod.ApiToken)
                }
            }
        }

        return methodChange(BbCloudAuthenticationMethod.None) to true
    }

    /**
     * Checks OAuth, returning the resolved outcome, or null when OAuth is not working so the caller continues with the
     * API token. The access token is fetched through the provider so it is refreshed proactively.
     */
    private suspend fun checkOAuth(): Pair<BbCloudAuthenticationMethod, Boolean>? {
        val bearer = accessTokenProvider.oauthAccessTokenOrNull()
        if (bearer == null) {
            accessTokenProvider.markOAuthNotWorking()
            return null
        }
        return when (val result = checkClient.check(bearerToken = bearer)) {
            is CredentialCheckResult.Valid -> {
                methodChange(BbCloudAuthenticationMethod.OAuth) to true
            }
            is CredentialCheckResult.Unauthorized -> {
                val refreshed = accessTokenProvider.refreshAfterUnauthorized(rejectedToken = bearer)
                if (refreshed != null && refreshed != bearer && isOAuthToken(token = refreshed)) {
                    when (checkClient.check(bearerToken = refreshed)) {
                        is CredentialCheckResult.Valid -> {
                            methodChange(BbCloudAuthenticationMethod.OAuth) to true
                        }
                        else -> {
                            accessTokenProvider.markOAuthNotWorking()
                            null
                        }
                    }
                } else {
                    accessTokenProvider.markOAuthNotWorking()
                    null
                }
            }
            is CredentialCheckResult.Forbidden -> {
                // Missing consumer scopes: OAuth cannot serve the plugin, so fall back to the API token.
                accessTokenProvider.markOAuthNotWorking()
                null
            }
            is CredentialCheckResult.Unavailable -> {
                offlineFallback(stored = BbCloudAuthenticationMethod.OAuth)
            }
        }
    }

    private suspend fun isOAuthToken(token: String): Boolean {
        return credentialStore.getOAuthTokens()?.accessToken == token
    }

    /**
     * When a check could not reach Bitbucket, use the last definite result if there is one; otherwise treat the stored
     * credential as working so the UI is not locked while offline. Never cached as definite.
     */
    private fun offlineFallback(stored: BbCloudAuthenticationMethod): Pair<BbCloudAuthenticationMethod, Boolean> {
        val fallback = lastKnownWorking ?: stored
        return fallback to false
    }

    private fun methodChange(method: BbCloudAuthenticationMethod): BbCloudAuthenticationMethod {
        val previous = cached?.method
        if (previous != null && previous != method) {
            LOG.info("Working authentication method changed: $previous -> $method")
        }
        return method
    }

    /** Drops the cache so the next [workingMethod] re-checks. Called after any credential change. */
    fun invalidate() {
        cached = null
    }

    companion object {
        private val LOG = logger<BbCloudCredentialValidator>()

        /** An offline (Unavailable) result is cached only briefly so the check retries soon. */
        private val UNAVAILABLE_CACHE_TTL: Duration = 30.seconds
    }
}