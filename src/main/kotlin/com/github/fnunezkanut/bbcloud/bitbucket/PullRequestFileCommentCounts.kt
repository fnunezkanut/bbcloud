package com.github.fnunezkanut.bbcloud.bitbucket

/**
 * Computes per-file comment counts for the "Files Changed" tab.
 * Counts are based on the raw comment list with filtering rules applied.
 */
class PullRequestFileCommentCounts private constructor(private val countsByNormalizedPath: Map<String, Int>) {
    fun countForChangePath(projectBasePath: String?, changeRelativePath: String): Int {
        val normalizedChangePath = PullRequestPathMatcher.normalize(changeRelativePath)
        var total = 0
        countsByNormalizedPath.forEach { (bitbucketPath, count) ->
            if (PullRequestPathMatcher.matches(
                    projectBasePath = projectBasePath,
                    changeRelativePath = normalizedChangePath,
                    bitbucketPath = bitbucketPath
                )
            ) {
                total += count
            }
        }
        return total
    }

    fun pathsWithComments(): Set<String> = countsByNormalizedPath.keys

    companion object {
        /**
         * Builds comment counts from the raw comment list.
         * - Drops comments where isDeleted == true or isPending == true (same as PullRequestCommentThreads)
         * - Keeps only comments with inlinePath != null (file-scoped). Discards PR-level comments (inlinePath == null)
         * - Groups by normalized Bitbucket path
         */
        fun from(comments: List<PullRequestComment>): PullRequestFileCommentCounts {
            val countsByPath = countVisibleFileScopedComments(comments)
            return PullRequestFileCommentCounts(countsByPath)
        }

        /** Choice A: count every surviving file-scoped comment, including replies. */
        private fun countVisibleFileScopedComments(comments: List<PullRequestComment>): Map<String, Int> {
            val countsByPath = mutableMapOf<String, Int>()
            comments.forEach { comment ->
                if (comment.isDeleted || comment.isPending) {
                    return@forEach
                }
                val inlinePath = comment.inlinePath ?: return@forEach
                val normalizedPath = PullRequestPathMatcher.normalize(inlinePath)
                countsByPath[normalizedPath] = (countsByPath[normalizedPath] ?: 0) + 1
            }
            return countsByPath.toMap()
        }
    }
}