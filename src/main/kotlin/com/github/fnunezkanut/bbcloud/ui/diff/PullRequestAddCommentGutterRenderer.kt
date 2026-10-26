package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.editor.ReviewInEditorUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUIUtil
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.ActiveGutterRenderer
import com.intellij.openapi.editor.markup.LineMarkerRendererEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.scale.JBUIScale
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.Icon

/** Paints and handles the single-line add-comment affordance in a review editor gutter. */
internal class PullRequestAddCommentGutterRenderer(
    private val project: Project,
    private val editor: EditorEx,
    private val relativePath: String,
    private val pullRequestId: Long,
    private val side: InlineCommentSide,
    private val service: BbCloudPullRequestDiffCommentService
) : LineMarkerRendererEx,
    ActiveGutterRenderer,
    Disposable {
    private var hoveredLogicalLine: Int? = null
    private var columnHovered = false

    private val hoverHandler = HoverHandler()

    init {
        editor.addEditorMouseListener(hoverHandler)
        editor.addEditorMouseMotionListener(hoverHandler)
        LOG.debug("Installed add-comment gutter renderer for pull request #$pullRequestId on $side")
    }

    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
        if (editor !== this.editor) {
            return
        }
        val line = hoveredLogicalLine ?: return
        if (!isCommentableLine(line)) {
            return
        }
        val editorImpl = this.editor as? EditorImpl ?: return
        val icon = EditorUIUtil.scaleIcon(ADD_COMMENT_ICON, editorImpl)
        val yRange = EditorUtil.logicalLineToYRange(this.editor, line).first
        val y = yRange.intervalStart() + this.editor.lineHeight / 2 - icon.iconHeight / 2
        icon.paintIcon(null, g, r.x, y)
    }

    override fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
        if (editor !== this.editor || !columnHovered || !isIconColumnHovered(editor = this.editor, event = e)) {
            return false
        }
        val line = hoveredLogicalLine ?: return false
        if (!isCommentableLine(line)) {
            return false
        }
        val editorImpl = this.editor as? EditorImpl ?: return false
        val icon = EditorUIUtil.scaleIcon(ADD_COMMENT_ICON, editorImpl)
        val yRange = EditorUtil.logicalLineToYRange(this.editor, line).first
        val iconY = yRange.intervalStart() + this.editor.lineHeight / 2 - icon.iconHeight / 2
        return e.y in iconY..(iconY + icon.iconHeight - 1)
    }

    override fun doAction(editor: Editor, e: MouseEvent) {
        if (!canDoAction(editor = editor, e = e)) {
            return
        }
        val line = hoveredLogicalLine ?: return
        LOG.info("Add Review Comment via gutter icon for pull request #$pullRequestId at line ${line + 1} ($side)")
        service.openInlineLineComposer(
            editor = this.editor,
            relativePath = relativePath,
            pullRequestId = pullRequestId,
            fileOneBasedLine = line + 1,
            side = side
        )
        e.consume()
    }

    override fun getTooltipText(): String {
        return CollaborationToolsBundle.message("review.editor.action.add.comment.text")
    }

    override fun getPosition(): LineMarkerRendererEx.Position {
        return LineMarkerRendererEx.Position.LEFT
    }

    override fun dispose() {
        editor.removeEditorMouseListener(hoverHandler)
        editor.removeEditorMouseMotionListener(hoverHandler)
        LOG.debug("Disposed add-comment gutter renderer for pull request #$pullRequestId on $side")
    }

    private fun isCommentableLine(line: Int): Boolean {
        if (line !in 0 until editor.document.lineCount) {
            return false
        }
        if (ReviewInEditorUtil.isLastBlankLine(editor.document, line)) {
            return false
        }
        return PullRequestReviewCommentTarget.canOfferPullRequestLineComment(
            project = project,
            file = editor.virtualFile,
            pullRequestId = pullRequestId
        )
    }

    private inner class HoverHandler :
        EditorMouseListener,
        EditorMouseMotionListener {
        override fun mouseMoved(e: EditorMouseEvent) {
            val line = e.logicalPosition.line
            val previousLine = hoveredLogicalLine
            hoveredLogicalLine = line.takeIf { it in 0 until editor.document.lineCount }
            columnHovered = isIconColumnHovered(editor = editor, event = e.mouseEvent)
            if (previousLine != null) {
                repaintColumn(line = previousLine)
            }
            repaintColumn(line = hoveredLogicalLine)
        }

        override fun mouseExited(e: EditorMouseEvent) {
            repaintColumn(line = hoveredLogicalLine)
            hoveredLogicalLine = null
            columnHovered = false
        }
    }

    private fun repaintColumn(line: Int?) {
        val gutter = editor.gutterComponentEx
        val xRange = iconColumnXRange(editor = editor)
        if (line != null && line > 0) {
            val y = editor.logicalPositionToXY(LogicalPosition(line, 0)).y
            gutter.repaint(xRange.first, y, xRange.last - xRange.first + 1, editor.lineHeight * 2)
        } else {
            gutter.repaint(xRange.first, 0, xRange.last - xRange.first + 1, gutter.height)
        }
    }

    companion object {
        private val LOG = logger<PullRequestAddCommentGutterRenderer>()
        private val ADD_COMMENT_ICON: Icon = IconLoader.getIcon(
            "/icons/expui/reviewGutter/reviewAddComment.svg",
            PullRequestAddCommentGutterRenderer::class.java
        )

        private fun isIconColumnHovered(editor: EditorEx, event: MouseEvent): Boolean {
            if (event.component !== editor.gutter) {
                return false
            }
            return event.x in iconColumnXRange(editor = editor)
        }

        private fun iconColumnXRange(editor: EditorEx): IntRange {
            val gutter = editor.gutterComponentEx
            val scaledIconWidth = JBUIScale.scale(ADD_COMMENT_GUTTER_ICON_WIDTH)
            val iconAreaWidth = if (editor is EditorImpl && ExperimentalUI.isNewUI()) {
                EditorUIUtil.scaleWidth(scaledIconWidth, editor) + 2
            } else {
                scaledIconWidth
            }
            val iconStart = if (editor.verticalScrollbarOrientation == EditorEx.VERTICAL_SCROLLBAR_RIGHT) {
                gutter.lineMarkerAreaOffset
            } else {
                gutter.width - gutter.lineMarkerAreaOffset - iconAreaWidth
            }
            return iconStart..(iconStart + iconAreaWidth - 1)
        }
    }
}