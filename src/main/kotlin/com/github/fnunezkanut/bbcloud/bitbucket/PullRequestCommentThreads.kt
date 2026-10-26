package com.github.fnunezkanut.bbcloud.bitbucket

/** Groups pull request comments into threads using the same rules as the activity timeline. */
object PullRequestCommentThreads {
    /**
     * Applies the same root-date descending order as [PullRequestTimelineBuilder.buildConversation].
     */
    fun sortedLikeConversation(threads: List<TimelineEntry.CommentThread>): List<TimelineEntry.CommentThread> {
        return threads.sortedByDescending { thread -> thread.date }
    }

    /**
     * Returns all inline threads for a repository-relative file, including both path-only and line-anchored roots.
     */
    fun threadsForFilePath(comments: List<PullRequestComment>, projectBasePath: String?, relativePath: String): List<TimelineEntry.CommentThread> {
        return sortedLikeConversation(
            groupIntoThreads(comments = comments).filter { thread ->
                val path = thread.root.inlinePath
                path != null && PullRequestPathMatcher.matches(
                    projectBasePath = projectBasePath,
                    changeRelativePath = relativePath,
                    bitbucketPath = path
                )
            }
        )
    }

    /**
     * Pending comments are dropped. Deleted comments are omitted from thread bodies; when the root comment is deleted the
     * whole thread is omitted. Replies, including replies to replies, are attached to their root comment; a reply whose
     * parent was dropped becomes the root of its own thread.
     */
    fun groupIntoThreads(comments: List<PullRequestComment>): List<TimelineEntry.CommentThread> {
        val visible = comments.filterNot { comment -> comment.isPending }
        val visibleById = visible.associateBy { comment -> comment.id }
        return visible
            .groupBy { comment -> rootOf(comment = comment, commentsById = visibleById).id }
            .mapNotNull { (rootId, threadComments) ->
                val root = visibleById[rootId] ?: return@mapNotNull null
                if (root.isDeleted) {
                    return@mapNotNull null
                }
                val replies = threadComments
                    .filterNot { comment -> comment.id == rootId }
                    .filterNot { comment -> comment.isDeleted }
                    .sortedBy { comment -> comment.createdOn }
                TimelineEntry.CommentThread(root = root, replies = replies)
            }
    }

    private fun rootOf(comment: PullRequestComment, commentsById: Map<Long, PullRequestComment>): PullRequestComment {
        var current = comment
        val visitedIds = mutableSetOf(comment.id)
        while (true) {
            val parent = current.parentId?.let { parentId -> commentsById[parentId] } ?: return current
            if (!visitedIds.add(parent.id)) {
                return current
            }
            current = parent
        }
    }
}