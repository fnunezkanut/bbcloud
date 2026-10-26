package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentAnchor

data class CommentLineIndicatorRange(val startLine: Int, val endLine: Int)

/**
 * Resolves inline comment anchors to merged zero-based editor line ranges for the content indicator.
 *
 * File-scoped and unmapped anchors intentionally use line zero. The fallback keeps a comment visible as a marker while
 * leaving [BbCloudPullRequestDiffCommentService] free to skip an inlay whose source line is not present.
 */
internal fun indicatorRangesForAnchors(
    anchors: List<InlineCommentAnchor>,
    editorLineRangeForAnchor: (InlineCommentAnchor) -> IntRange?
): List<CommentLineIndicatorRange> {
    val indicatorLines = buildSet {
        anchors.forEach { anchor ->
            val editorRange = if (anchor.isFileScoped) {
                FILE_SCOPED_INDICATOR_LINE..FILE_SCOPED_INDICATOR_LINE
            } else {
                editorLineRangeForAnchor(anchor) ?: UNMAPPED_FALLBACK_LINE..UNMAPPED_FALLBACK_LINE
            }
            editorRange.forEach { line ->
                if (line >= 0) {
                    add(line)
                }
            }
        }
    }
    if (indicatorLines.isEmpty()) {
        return emptyList()
    }

    val sortedLines = indicatorLines.sorted()
    val ranges = mutableListOf<CommentLineIndicatorRange>()
    var rangeStart = sortedLines.first()
    var rangeEnd = rangeStart
    sortedLines.drop(1).forEach { line ->
        if (line == rangeEnd + 1) {
            rangeEnd = line
        } else {
            ranges += CommentLineIndicatorRange(startLine = rangeStart, endLine = rangeEnd)
            rangeStart = line
            rangeEnd = line
        }
    }
    ranges += CommentLineIndicatorRange(startLine = rangeStart, endLine = rangeEnd)
    return ranges
}

internal const val FILE_SCOPED_INDICATOR_LINE = 0
internal const val UNMAPPED_FALLBACK_LINE = 0