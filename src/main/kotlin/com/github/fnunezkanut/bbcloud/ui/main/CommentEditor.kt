package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.CommentChange
import com.intellij.openapi.project.Project

/** Comment operations of the displayed pull request; implementations call back on the event dispatch thread. */
interface CommentEditor {
    /** Null until the tool window is bound to a project. */
    fun project(): Project?

    /** [content] is Markdown; [onResult] receives true when Bitbucket accepted the comment. */
    fun addComment(content: String, onResult: (Boolean) -> Unit)

    /** [onResult] receives true when Bitbucket accepted the change. */
    fun applyCommentChange(change: CommentChange, onResult: (Boolean) -> Unit)
}