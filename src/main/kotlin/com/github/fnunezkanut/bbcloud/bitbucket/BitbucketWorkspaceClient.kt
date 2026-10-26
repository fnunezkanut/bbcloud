package com.github.fnunezkanut.bbcloud.bitbucket

import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

interface BitbucketWorkspaceClient {
    suspend fun listWorkspaces(apiToken: String): WorkspaceListResult
}

sealed class WorkspaceListResult {
    data class Success(val workspaces: List<BitbucketWorkspace>) : WorkspaceListResult()

    /** 401 or 403: the token is invalid, expired or missing the required scopes. */
    data object Unauthorized : WorkspaceListResult()

    data class Failed(val cause: Throwable? = null) : WorkspaceListResult()
}

class OkHttpBitbucketWorkspaceClient(private val httpClient: OkHttpClient = sharedHttpClient) : BitbucketWorkspaceClient {
    override suspend fun listWorkspaces(apiToken: String): WorkspaceListResult {
        return withContext(Dispatchers.IO) {
            val apiUrl = BitbucketWorkspaceResponseParser.buildListWorkspacesUrl()
            val request = Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer $apiToken")
                .header("Accept", "application/json")
                .get()
                .build()
            LOG.debug("GET $apiUrl")
            val startNanos = System.nanoTime()
            try {
                httpClient.newCall(request).execute().use { response ->
                    LOG.debug("GET $apiUrl -> ${response.code} in ${elapsedMillis(startNanos = startNanos)} ms")
                    when (response.code) {
                        200 -> {
                            WorkspaceListResult.Success(BitbucketWorkspaceResponseParser.parseWorkspaces(response.body.string()))
                        }
                        401, 403 -> {
                            WorkspaceListResult.Unauthorized
                        }
                        else -> {
                            LOG.warn("Unexpected HTTP status ${response.code} for GET $apiUrl${BitbucketHttpErrors.describe(response)}")
                            WorkspaceListResult.Failed()
                        }
                    }
                }
            } catch (exception: IOException) {
                LOG.debug("GET $apiUrl failed after ${elapsedMillis(startNanos = startNanos)} ms", exception)
                WorkspaceListResult.Failed(exception)
            }
        }
    }

    companion object {
        private val LOG = logger<OkHttpBitbucketWorkspaceClient>()

        private val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        private fun elapsedMillis(startNanos: Long): Long {
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
        }
    }
}