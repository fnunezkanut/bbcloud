package com.github.fnunezkanut.bbcloud.auth

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketApiUrlPolicy
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketHttpErrors
import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketPullRequestResponseParser
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The outcome of an online credential check against `GET /2.0/user`. */
sealed class CredentialCheckResult {
    data object Valid : CredentialCheckResult()

    /** 401: the token is invalid, expired, or revoked. */
    data object Unauthorized : CredentialCheckResult()

    /** 403: the token works but lacks the required scopes. */
    data object Forbidden : CredentialCheckResult()

    /** I/O error, 5xx, or 429: the check could not be completed; the credential state is unknown. */
    data class Unavailable(val cause: Throwable? = null) : CredentialCheckResult()
}

interface BitbucketCredentialCheckClient {
    suspend fun check(bearerToken: String): CredentialCheckResult
}

/**
 * Checks a bearer token against `GET /2.0/user`. It has its own short-timeout OkHttp client so the configured gate
 * stays fast, and it carries no 401 authenticator because it handles 401 explicitly. The response body is not read.
 */
class OkHttpBitbucketCredentialCheckClient(private val httpClient: OkHttpClient = sharedHttpClient) : BitbucketCredentialCheckClient {
    override suspend fun check(bearerToken: String): CredentialCheckResult {
        return withContext(Dispatchers.IO) {
            val url = BitbucketPullRequestResponseParser.buildCurrentUserUrl()
            if (!BitbucketApiUrlPolicy.isTrustedApiUrl(url)) {
                LOG.warn("Refusing to send the token to an untrusted URL (${BitbucketApiUrlPolicy.describeRejected(url)})")
                return@withContext CredentialCheckResult.Unavailable()
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $bearerToken")
                .header("Accept", "application/json")
                .get()
                .build()
            LOG.debug("GET $url (credential check)")
            val startNanos = System.nanoTime()
            try {
                httpClient.newCall(request).execute().use { response ->
                    LOG.debug("GET $url -> ${response.code} in ${elapsedMillis(startNanos = startNanos)} ms")
                    when (response.code) {
                        200 -> {
                            CredentialCheckResult.Valid
                        }
                        401 -> {
                            CredentialCheckResult.Unauthorized
                        }
                        403 -> {
                            CredentialCheckResult.Forbidden
                        }
                        429 -> {
                            CredentialCheckResult.Unavailable()
                        }
                        in 500..599 -> {
                            LOG.warn("Unexpected HTTP status ${response.code} for GET $url${BitbucketHttpErrors.describe(response)}")
                            CredentialCheckResult.Unavailable()
                        }
                        else -> {
                            LOG.warn("Unexpected HTTP status ${response.code} for GET $url${BitbucketHttpErrors.describe(response)}")
                            CredentialCheckResult.Unavailable()
                        }
                    }
                }
            } catch (exception: IOException) {
                LOG.debug("GET $url failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
                CredentialCheckResult.Unavailable(cause = exception)
            }
        }
    }

    companion object {
        private val LOG = logger<OkHttpBitbucketCredentialCheckClient>()

        private val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        private fun elapsedMillis(startNanos: Long): Long {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        }
    }
}