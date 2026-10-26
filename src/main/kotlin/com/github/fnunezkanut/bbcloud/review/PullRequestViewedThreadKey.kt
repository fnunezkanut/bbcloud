package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher

/** An encoded viewed-thread entry containing its key and current content fingerprint. */
data class ViewedThreadEntry(val key: String, val fingerprint: String)

/** Builds and decodes keys and persisted entries for viewed pull-request comment threads. */
object PullRequestViewedThreadKey {
    /** The normalized path used for pull-request-level comment threads. */
    const val PR_LEVEL_PATH_SENTINEL = ""

    /** Builds a workspace-scoped key from a pull request, thread path, and root comment. */
    fun of(workspaceSlug: String, pullRequestId: Long, relativePath: String, rootCommentId: Long): String {
        return "$workspaceSlug/$pullRequestId:${PullRequestPathMatcher.normalize(relativePath)}:$rootCommentId"
    }

    /** Encodes a viewed-thread key and its current content fingerprint as one persisted entry. */
    fun entryOf(key: String, fingerprint: String): String {
        return "$key@$fingerprint"
    }

    /** Decodes an entry using its last at-sign as the key/fingerprint separator, or null when it has none. */
    fun decodeEntry(entry: String): ViewedThreadEntry? {
        val separator = entry.lastIndexOf('@')
        if (separator < 0) {
            return null
        }
        return ViewedThreadEntry(
            key = entry.substring(0, separator),
            fingerprint = entry.substring(separator + 1)
        )
    }

    /** Returns the workspace slug encoded before the first slash. */
    fun workspaceSlugOf(key: String): String {
        return key.substringBefore('/')
    }

    /** Returns the pull request number encoded after the workspace slug and before the first colon. */
    fun pullRequestIdOf(key: String): Long? {
        return key.substringAfter('/').substringBefore(':').toLongOrNull()
    }

    /** Returns the root comment id encoded after the last colon. */
    fun rootCommentIdOf(key: String): Long? {
        return key.substringAfterLast(':').toLongOrNull()
    }

    /** Returns stale keys for one workspace and pull request without touching other scopes. */
    fun keysToPrune(keys: Set<String>, workspaceSlug: String, pullRequestId: Long, keptRootCommentIds: Set<Long>): Set<String> {
        return keys.filterTo(mutableSetOf()) { key ->
            workspaceSlugOf(key) == workspaceSlug &&
                pullRequestIdOf(key) == pullRequestId &&
                rootCommentIdOf(key) !in keptRootCommentIds
        }
    }
}