package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry

/** Builds a stable content fingerprint for a pull-request comment thread. */
object PullRequestThreadFingerprint {
    /**
     * Fingerprints ids, deletion state, and comment bodies because the comment model has no edit timestamp.
     *
     * The body-sensitive value intentionally changes when a comment is edited, added, or deleted.
     */
    fun of(thread: TimelineEntry.CommentThread): String {
        val comments = listOf(thread.root) + thread.replies
        val joined = comments.joinToString(separator = "|") { comment ->
            val bodyHash = (comment.raw ?: comment.html).hashCode()
            "${comment.id}:${comment.isDeleted}:$bodyHash"
        }
        return joined.hashCode().toString()
    }
}