package com.github.fnunezkanut.bbcloud.ui.diff

import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import java.awt.Graphics

/** Paints the purple content-edge marker for one or more contiguous pull request comment lines. */
internal class PullRequestCommentLineIndicatorRenderer(private val startLine: Int, private val endLine: Int) : CustomHighlighterRenderer {
    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
        val yRange = editorLineYRange(editor = editor, startLine = startLine, endLine = endLine)
        g.color = JBColor.namedColor(
            COMMENT_LINE_INDICATOR_COLOR_KEY,
            JBColor(0x8E44AD, 0xB794F4)
        )
        val padding = JBUIScale.scale(COMMENT_LINE_INDICATOR_PADDING)
        g.fillRect(
            padding,
            yRange.first,
            JBUIScale.scale(COMMENT_LINE_INDICATOR_WIDTH),
            (yRange.last - yRange.first).coerceAtLeast(1)
        )
    }

    companion object {
        private const val COMMENT_LINE_INDICATOR_COLOR_KEY = "BbCloud.Review.CommentLineIndicator"
        internal const val COMMENT_LINE_INDICATOR_WIDTH = 3
        internal const val COMMENT_LINE_INDICATOR_PADDING = 3
    }
}

/** Left inset for the editor content area: padding, bar, and matching padding before text. */
internal fun commentLineIndicatorContentInsetPx(): Int {
    return JBUIScale.scale(
        PullRequestCommentLineIndicatorRenderer.COMMENT_LINE_INDICATOR_PADDING +
            PullRequestCommentLineIndicatorRenderer.COMMENT_LINE_INDICATOR_WIDTH +
            PullRequestCommentLineIndicatorRenderer.COMMENT_LINE_INDICATOR_PADDING
    )
}

/** Local copy of the platform helper because its implementation is internal to the IntelliJ collaboration module. */
private fun editorLineYRange(editor: Editor, startLine: Int, endLine: Int): IntRange {
    if (editor.document.lineCount == 0) {
        return 0..editor.lineHeight
    }
    val maxLine = (editor.document.lineCount - 1).coerceAtLeast(0)
    val boundedStartLine = startLine.coerceIn(0, maxLine)
    val boundedEndLine = endLine.coerceIn(0, maxLine)
    val startOffset = editor.document.getLineStartOffset(boundedStartLine)
    val endOffset = editor.document.getLineEndOffset(boundedEndLine)
    val startY = editor.offsetToXY(startOffset).y
    val foldRegion = editor.foldingModel.getCollapsedRegionAtOffset((endOffset - 1).coerceAtLeast(0)) as? CustomFoldRegion
    val endY = foldRegion?.location?.let { location -> location.y + foldRegion.heightInPixels }
        ?: (editor.offsetToXY(endOffset).y + editor.lineHeight)
    return startY..endY
}