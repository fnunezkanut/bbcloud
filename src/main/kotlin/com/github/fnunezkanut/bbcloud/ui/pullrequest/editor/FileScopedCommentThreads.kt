package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestCommentThreads
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry

/** Returns all inline threads for [relativePath], keeping replies attached and matching Conversation order. */
internal fun fileScopedThreadsForPath(comments: List<PullRequestComment>, projectBasePath: String?, relativePath: String): List<TimelineEntry.CommentThread> {
    return PullRequestCommentThreads.threadsForFilePath(
        comments = comments,
        projectBasePath = projectBasePath,
        relativePath = relativePath
    )
}