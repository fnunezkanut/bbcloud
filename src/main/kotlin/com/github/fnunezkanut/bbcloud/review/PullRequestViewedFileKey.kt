package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher

/** An encoded viewed-file entry containing its key and source-branch revision. */
data class ViewedFileEntry(val key: String, val revision: String)

/** Builds and decodes keys and persisted entries for viewed pull-request files. */
object PullRequestViewedFileKey {
    /** The revision used for a file deleted at the pull request source revision. */
    const val DELETED_REVISION = ""

    /** Builds a key from a pull request number and a normalized project-relative path. */
    fun of(pullRequestId: Long, relativePath: String): String {
        return "$pullRequestId:${PullRequestPathMatcher.normalize(relativePath)}"
    }

    /** Returns the pull request number encoded before the first colon, or null when it is invalid. */
    fun pullRequestIdOf(key: String): Long? {
        return key.substringBefore(':').toLongOrNull()
    }

    /** Returns active-pull-request keys whose paths are not present in the current changed-file set. */
    fun keysToPrune(keys: Set<String>, pullRequestId: Long, keptPaths: Set<String>): Set<String> {
        val normalizedKeptPaths = keptPaths.map { path -> PullRequestPathMatcher.normalize(path) }.toSet()
        return keys.filterTo(mutableSetOf()) { key ->
            if (pullRequestIdOf(key) != pullRequestId) {
                false
            } else {
                val pathSeparator = key.indexOf(':')
                pathSeparator >= 0 &&
                    PullRequestPathMatcher.normalize(key.substring(pathSeparator + 1)) !in normalizedKeptPaths
            }
        }
    }

    /** Encodes a viewed-file key and its source-branch revision as one persisted entry. */
    fun entryOf(key: String, revision: String): String {
        return "$key@$revision"
    }

    /** Decodes an entry using its last at-sign as the key/revision separator, or null when it has none. */
    fun decodeEntry(entry: String): ViewedFileEntry? {
        val separator = entry.lastIndexOf('@')
        if (separator < 0) {
            return null
        }
        return ViewedFileEntry(
            key = entry.substring(0, separator),
            revision = entry.substring(separator + 1)
        )
    }
}