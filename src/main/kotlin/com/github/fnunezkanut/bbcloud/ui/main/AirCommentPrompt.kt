package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide

/**
 * Builds the text pasted into the JetBrains Air chat input for a pull request comment. The text names the commented
 * location and quotes the comment in a fenced block.
 */
internal object AirCommentPrompt {
    private const val BASE_FENCE = "\"\"\""
    private const val COMMENT_LINE = "comment left"
    private const val GENERAL_COMMENT_SUFFIX = "general comment"

    /** The full prompt. [location] is the first line, see [locationLine]. */
    fun build(location: String, commentText: String): String {
        val fence = fenceFor(commentText = commentText)
        return buildString {
            append(location).append('\n')
            append(COMMENT_LINE).append('\n')
            append(fence).append('\n')
            append(commentText).append('\n')
            append(fence)
        }
    }

    /**
     * The first line of the prompt: the absolute path with the line label, or only the path when the comment has no line.
     * A pull request-level comment has no file, so the line names the pull request.
     */
    fun locationLine(absolutePath: String?, lineLabel: String?, pullRequestId: Long?): String {
        if (absolutePath == null) {
            val pullRequestLabel = pullRequestId?.let { id -> " #$id" }.orEmpty()
            return "Pull request$pullRequestLabel $GENERAL_COMMENT_SUFFIX"
        }
        if (lineLabel == null) {
            return absolutePath
        }
        return "$absolutePath line: $lineLabel"
    }

    /**
     * The line label of an inline comment: `12`, `10-12` for a multi-line range, and a ` (old side)` suffix for a comment
     * on the removed side of the diff. Null when the comment has no line.
     */
    fun lineLabel(inlineLine: Int?, inlineLineEnd: Int?, side: InlineCommentSide?): String? {
        if (inlineLine == null) {
            return null
        }
        val range = if (inlineLineEnd != null && inlineLineEnd != inlineLine) {
            "$inlineLine-$inlineLineEnd"
        } else {
            "$inlineLine"
        }
        return if (side == InlineCommentSide.BEFORE) "$range (old side)" else range
    }

    /** Adds quotes to the fence until it no longer occurs in [commentText], so the comment cannot close the block early. */
    internal fun fenceFor(commentText: String): String {
        var fence = BASE_FENCE
        while (commentText.contains(fence)) {
            fence += "\""
        }
        return fence
    }
}