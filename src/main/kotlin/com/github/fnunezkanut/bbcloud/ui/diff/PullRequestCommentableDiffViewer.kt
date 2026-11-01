package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentAnchor
import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.ex.EditorEx

/**
 * A diff viewer that can host pull request comment threads, with the editor of each inline side.
 *
 * A side-by-side diff (a modified or moved file) has both editors. A oneside diff of an added file shows only the
 * source (AFTER) side, so it has no BEFORE editor. A oneside diff of a deleted file is not commentable (its path no
 * longer exists on the source branch) and is never wrapped.
 *
 * This is a plain class, not a data class: its equality must never be used as a map key. The service keeps keying its
 * sessions by the underlying [viewer].
 */
internal class PullRequestCommentableDiffViewer private constructor(
    val viewer: DiffViewerBase,
    private val beforeEditor: EditorEx?,
    private val afterEditor: EditorEx?
) {
    /** True when the viewer shows only the source (AFTER) side, i.e. an added-file oneside diff. */
    val isOneside: Boolean = beforeEditor == null

    /** The editor hosting comments for [side], or null when the viewer has no editor on that side. */
    fun editorFor(side: InlineCommentSide): EditorEx? {
        return when (side) {
            InlineCommentSide.BEFORE -> beforeEditor
            InlineCommentSide.AFTER -> afterEditor
        }
    }

    /** The inline side [editor] belongs to, or null when [editor] is not part of this viewer. */
    fun sideOf(editor: EditorEx): InlineCommentSide? {
        if (afterEditor != null && afterEditor === editor) {
            return InlineCommentSide.AFTER
        }
        if (beforeEditor != null && beforeEditor === editor) {
            return InlineCommentSide.BEFORE
        }
        return null
    }

    companion object {
        /** Wraps [viewer] when it is a side-by-side text diff or a oneside text diff of an added file. */
        fun from(viewer: Any?): PullRequestCommentableDiffViewer? {
            return when (viewer) {
                is TwosideTextDiffViewer -> PullRequestCommentableDiffViewer(
                    viewer = viewer,
                    beforeEditor = viewer.getEditor(Side.LEFT),
                    afterEditor = viewer.getEditor(Side.RIGHT)
                )
                is OnesideTextDiffViewer -> if (viewer.side == Side.RIGHT) {
                    PullRequestCommentableDiffViewer(
                        viewer = viewer,
                        beforeEditor = null,
                        afterEditor = viewer.getEditor()
                    )
                } else {
                    null
                }
                else -> null
            }
        }
    }
}

/**
 * The inline side of [editor] in the first of [viewers] that owns it, or null when none does. A [DiffRequestProcessor]
 * element is resolved through its active viewer; any other element is wrapped directly.
 */
internal fun sideOfEditor(editor: EditorEx, viewers: Iterable<Any?>): InlineCommentSide? {
    viewers.forEach { candidate ->
        val resolved = if (candidate is DiffRequestProcessor) {
            candidate.activeViewer
        } else {
            candidate
        }
        val side = PullRequestCommentableDiffViewer.from(viewer = resolved)?.sideOf(editor = editor)
        if (side != null) {
            return side
        }
    }
    return null
}

/**
 * The zero-based editor line an inline comment thread inlay is placed on, or null when the thread must be skipped.
 *
 * A file-scoped thread and a thread forced to the top of the file (an orphan pre-image anchor shown on an added file)
 * go to line 0. Every other thread uses its [mappedLine]; a line that could not be mapped into the editor is skipped.
 */
internal fun threadInlayLine(anchor: InlineCommentAnchor, mappedLine: Int?, forceTopOfFile: Boolean): Int? {
    if (anchor.isFileScoped || forceTopOfFile) {
        return 0
    }
    return mappedLine
}