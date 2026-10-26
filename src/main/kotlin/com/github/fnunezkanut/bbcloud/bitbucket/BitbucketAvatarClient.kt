package com.github.fnunezkanut.bbcloud.bitbucket

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Downloads author avatar images. Avatar URLs point to public CDNs outside Bitbucket,
 * so requests must never carry the Bitbucket API token.
 */
class BitbucketAvatarClient(private val httpClient: OkHttpClient = sharedHttpClient) {
    suspend fun fetchAvatar(url: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            val request = runCatching { Request.Builder().url(url).get().build() }.getOrElse { exception ->
                LOG.debug("Invalid avatar URL $url", exception)
                return@withContext null
            }
            LOG.debug("GET $url")
            val startNanos = System.nanoTime()
            try {
                httpClient.newCall(request).execute().use { response ->
                    LOG.debug("GET $url -> ${response.code} in ${elapsedMillis(startNanos = startNanos)} ms")
                    if (!response.isSuccessful) {
                        return@withContext null
                    }
                    val source = response.body.source()
                    source.request(MAX_AVATAR_BYTES + 1)
                    if (source.buffer.size > MAX_AVATAR_BYTES) {
                        LOG.debug("Avatar at $url exceeds $MAX_AVATAR_BYTES bytes")
                        return@withContext null
                    }
                    source.readByteArray()
                }
            } catch (exception: Exception) {
                LOG.debug("GET $url failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
                null
            }
        }
    }

    companion object {
        private val LOG = logger<BitbucketAvatarClient>()

        private const val MAX_AVATAR_BYTES = 1024L * 1024L

        private val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        private fun elapsedMillis(startNanos: Long): Long {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        }
    }
}