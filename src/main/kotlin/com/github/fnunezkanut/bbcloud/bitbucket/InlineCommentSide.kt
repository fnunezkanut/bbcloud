package com.github.fnunezkanut.bbcloud.bitbucket

/** Which side of a pull request diff an inline comment refers to. */
enum class InlineCommentSide {
    /** Line on the destination (before) side — Bitbucket `inline.from`. */
    BEFORE,

    /** Line on the source (after) side — Bitbucket `inline.to`. */
    AFTER
}