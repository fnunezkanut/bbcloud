package com.github.fnunezkanut.bbcloud.bitbucket

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

interface BitbucketRepositoryClient {
    suspend fun checkRepositoryExists(workspace: String, repoSlug: String, apiToken: String): RepositoryCheckResult
}

sealed class RepositoryCheckResult {
    /**
     * The repository exists. [avatarUrl] carries the repository's configured avatar/icon URL
     * (`links.avatar.href`) when the 200 body included one, or null when it was absent, blank, or the body could not
     * be parsed. The avatar URL points to a public CDN, so it must never be requested with the Bitbucket API token.
     */
    data class Exists(val avatarUrl: String? = null) : RepositoryCheckResult()

    data object NotFound : RepositoryCheckResult()

    data class Failed(val cause: Throwable? = null) : RepositoryCheckResult()
}

class OkHttpBitbucketRepositoryClient(private val httpClient: OkHttpClient = sharedHttpClient) : BitbucketRepositoryClient {
    override suspend fun checkRepositoryExists(workspace: String, repoSlug: String, apiToken: String): RepositoryCheckResult {
        return withContext(Dispatchers.IO) {
            // Request only the avatar href via Bitbucket's partial-response `fields` parameter so the 200 payload stays
            // tiny; the status code still decides existence and the href is a bonus read when present.
            val apiUrl = "$BITBUCKET_API_BASE/repositories/$workspace/$repoSlug?fields=links.avatar.href"
            val request = Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer $apiToken")
                .get()
                .build()
            LOG.debug("GET $apiUrl")
            val startNanos = System.nanoTime()
            try {
                httpClient.newCall(request).execute().use { response ->
                    LOG.debug("GET $apiUrl -> ${response.code} in ${elapsedMillis(startNanos = startNanos)} ms")
                    when (response.code) {
                        200 -> {
                            RepositoryCheckResult.Exists(avatarUrl = parseAvatarUrl(body = response.body.string()))
                        }
                        404 -> {
                            RepositoryCheckResult.NotFound
                        }
                        else -> {
                            LOG.warn("Unexpected HTTP status ${response.code} for GET $apiUrl${BitbucketHttpErrors.describe(response)}")
                            RepositoryCheckResult.Failed()
                        }
                    }
                }
            } catch (exception: IOException) {
                LOG.debug("GET $apiUrl failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
                RepositoryCheckResult.Failed(exception)
            }
        }
    }

    /**
     * Reads `links.avatar.href` from a 200 repository body, returning null when it is absent, blank, or the body does
     * not parse. A parse failure must NOT downgrade a real 200 to [RepositoryCheckResult.Failed] (that would wrongly
     * drop an existing repository), so it is logged at `debug` and treated as "no avatar".
     */
    private fun parseAvatarUrl(body: String): String? {
        return try {
            val parsed = json.decodeFromString(RepositoryResponse.serializer(), body)
            parsed.links?.avatar?.href?.takeIf { it.isNotBlank() }
        } catch (exception: Exception) {
            LOG.debug("Could not parse repository avatar href from a 200 body", exception)
            null
        }
    }

    @Serializable
    private data class RepositoryResponse(val links: Links? = null) {
        @Serializable
        data class Links(val avatar: Avatar? = null)

        @Serializable
        data class Avatar(
            @SerialName("href") val href: String? = null
        )
    }

    companion object {
        private val LOG = logger<OkHttpBitbucketRepositoryClient>()

        private const val BITBUCKET_API_BASE = "https://api.bitbucket.org/2.0"

        private val json = Json {
            ignoreUnknownKeys = true
        }

        private val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        private fun elapsedMillis(startNanos: Long): Long {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        }
    }
}