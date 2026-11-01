package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.util.side.OnesideDiffViewer
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.vcs.changes.Change

/**
 * Where a review-bar "Add Comment" click posts its comment.
 *
 * A file the pull request modifies, adds or moves still exists on the source branch, so its comment is anchored to the
 * path ([FileScoped]). A file the pull request deletes has no file on the source branch, so a path anchor would point
 * at a path that no longer exists; its comment is posted on the whole pull request instead ([PullRequestLevel]).
 */
internal sealed interface ReviewBarCommentScope {
    /** The comment is anchored to the repository-relative path of the file. */
    data object FileScoped : ReviewBarCommentScope

    /** The comment is posted on the whole pull request, used for a deleted file whose path is gone. */
    data object PullRequestLevel : ReviewBarCommentScope
}

/** Picks the comment scope of a diff from its change type and its oneside diff side. */
internal object ReviewBarCommentScopes {
    /**
     * Resolves the comment scope for a diff.
     *
     * A deleted path has no file on the source branch, so the comment is posted on the pull request instead of being
     * anchored to a path that no longer exists. A left-only oneside diff shows only the before side (the after side is
     * empty), so it is treated the same way even when [changeType] is unknown. Every other diff keeps a file-scoped
     * comment.
     */
    fun scopeFor(changeType: Change.Type?, onesideSide: Side?): ReviewBarCommentScope {
        if (changeType == Change.Type.DELETED) {
            return ReviewBarCommentScope.PullRequestLevel
        }
        if (onesideSide == Side.LEFT) {
            return ReviewBarCommentScope.PullRequestLevel
        }
        return ReviewBarCommentScope.FileScoped
    }
}

/**
 * Resolves the editor and oneside side of a diff viewer for the review bar.
 *
 * The helpers take [Any] because the two call sites hand in different platform handles: the diff extension passes a
 * `FrameDiffTool.DiffViewer`, while the editor-tab provider passes a [DiffRequestProcessor] (a `DiffEditorViewer`).
 * Both end up resolved to a concrete viewer here.
 */
internal object ReviewBarDiffEditors {
    /** The active viewer of a [DiffRequestProcessor], or [viewer] itself when it is a plain viewer. */
    fun activeViewer(viewer: Any?): Any? {
        if (viewer is DiffRequestProcessor) {
            return viewer.activeViewer
        }
        return viewer
    }

    /**
     * The editor the comment composer attaches to, resolved on click because the active viewer of a
     * [DiffRequestProcessor] can change.
     *
     * A side-by-side diff comments against its right (source) editor; a oneside diff (a deleted or added file) has a
     * single editor; a unified diff exposes its editor through [EditorDiffViewer]. Any other viewer has no text editor.
     */
    fun commentEditorFor(viewer: Any?): EditorEx? {
        val activeViewer = activeViewer(viewer = viewer)
        return when (activeViewer) {
            is TwosideTextDiffViewer -> activeViewer.getEditor(Side.RIGHT)
            is OnesideTextDiffViewer -> activeViewer.getEditor()
            is EditorDiffViewer -> activeViewer.currentEditor as? EditorEx
            else -> null
        }
    }

    /** The side shown by a oneside diff viewer (left for a deleted file, right for an added file), or null otherwise. */
    fun onesideSideOf(viewer: Any?): Side? {
        val activeViewer = activeViewer(viewer = viewer)
        return (activeViewer as? OnesideDiffViewer<*>)?.side
    }
}