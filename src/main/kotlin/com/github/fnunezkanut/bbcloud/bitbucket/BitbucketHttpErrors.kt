package com.github.fnunezkanut.bbcloud.bitbucket

import okhttp3.Response

internal object BitbucketHttpErrors {
    private const val MAX_ERROR_BODY_BYTES = 64L * 1024L

    fun describe(response: Response): String {
        val errorMessage = runCatching {
            BitbucketPullRequestResponseParser.parseErrorMessage(response.peekBody(MAX_ERROR_BODY_BYTES).string())
        }.getOrNull()
        val requestId = response.header("X-Request-Id")?.takeIf { it.isNotBlank() }
        val parts = listOfNotNull(
            errorMessage,
            requestId?.let { id -> "requestId=$id" }
        )
        return if (parts.isEmpty()) {
            ""
        } else {
            ": " + parts.joinToString(separator = ", ")
        }
    }
}