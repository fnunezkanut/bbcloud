package com.github.fnunezkanut.bbcloud.bitbucket

/** A change to an existing pull request comment, or a new comment. */
sealed interface CommentChange {
    /** The comment the change applies to; the comment being created by [Create] has none. */
    val commentId: Long?

    data class Create(val content: String) : CommentChange {
        override val commentId: Long? = null
    }

    /** Creates a comment anchored to a repository-relative file without a line anchor. */
    data class CreateFileScoped(val content: String, val path: String) : CommentChange {
        override val commentId: Long? = null
    }

    /**
     * Creates a comment anchored to a repository-relative file at a one-based [line].
     *
     * [side] selects the Bitbucket inline anchor: [InlineCommentSide.AFTER] writes `inline.to` (the source/after side,
     * which is the file on disk and the diff right editor), [InlineCommentSide.BEFORE] writes `inline.from` (the
     * pre-image, the diff left editor).
     */
    data class CreateInlineLine(val content: String, val path: String, val line: Int, val side: InlineCommentSide = InlineCommentSide.AFTER) : CommentChange {
        override val commentId: Long? = null
    }

    data class Reply(override val commentId: Long, val content: String) : CommentChange

    data class Edit(override val commentId: Long, val content: String) : CommentChange

    /** Resolves the root comment of a thread, including a pull-request-level comment. */
    data class Resolve(override val commentId: Long) : CommentChange

    /** Reopens the root comment of a resolved thread. */
    data class Unresolve(override val commentId: Long) : CommentChange

    data class Delete(override val commentId: Long) : CommentChange
}