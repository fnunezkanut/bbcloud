package com.github.fnunezkanut.bbcloud.ui.diff

import com.intellij.collaboration.ui.codereview.editor.CodeReviewEditorGutterControlsModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Gutter model for the platform code review renderer that offers the single-line add-comment affordance.
 * Existing threads are shown by the plugin's own inlays, so no lines report comments and toggling is a no-op.
 */
internal class PullRequestAddCommentGutterModel(private val canComment: (Int) -> Boolean, private val onRequestNewComment: (Int) -> Unit) :
    CodeReviewEditorGutterControlsModel {
    override val gutterControlsState: StateFlow<CodeReviewEditorGutterControlsModel.ControlsState?> =
        MutableStateFlow(State())

    override fun requestNewComment(lineIdx: Int) {
        onRequestNewComment(lineIdx)
    }

    override fun toggleComments(lineIdx: Int) {
        // no-op: linesWithComments is always empty
    }

    private inner class State : CodeReviewEditorGutterControlsModel.ControlsState {
        override val linesWithComments: Set<Int> = emptySet()

        override fun isLineCommentable(lineIdx: Int): Boolean {
            return canComment(lineIdx)
        }
    }
}