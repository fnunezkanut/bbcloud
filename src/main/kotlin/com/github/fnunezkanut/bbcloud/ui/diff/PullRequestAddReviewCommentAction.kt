package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.github.fnunezkanut.bbcloud.review.PullRequestCommentDecorationContext
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.editor.ReviewInEditorUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

/**
 * "Add Review Comment" (platform wording, decision 10b) at the top of the editor gutter popup and the editor text popup
 * (decision 7b). It opens a line-anchored inline composer on the gutter line (gutter popup) or the caret line (editor
 * popup), gated by the same 4b/6a/9a rules as the gutter plus (via [PullRequestReviewCommentTarget]).
 */
internal class PullRequestAddReviewCommentAction :
    DumbAwareAction(
        CollaborationToolsBundle.messagePointer("review.editor.action.add.comment.text"),
        CollaborationToolsBundle.messagePointer("review.editor.action.add.comment.description")
    ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = resolveTarget(event = event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val target = resolveTarget(event = event) ?: return
        LOG.info(
            "Add Review Comment via popup for pull request #${target.pullRequestId} at ${target.relativePath} " +
                "line ${target.fileOneBasedLine} (${target.side})"
        )
        // Mirror the platform pattern: move the caret to the end of the target line before opening the composer.
        val editorLine = target.fileOneBasedLine - 1
        if (editorLine in 0 until target.editor.document.lineCount) {
            target.editor.caretModel.moveToOffset(target.editor.document.getLineEndOffset(editorLine))
        }
        BbCloudPullRequestDiffCommentService.getInstance(target.project).openInlineLineComposer(
            editor = target.editor,
            relativePath = target.relativePath,
            pullRequestId = target.pullRequestId,
            fileOneBasedLine = target.fileOneBasedLine,
            side = target.side
        )
    }

    /** Resolves a commentable target from the event, or null when the action must be hidden/disabled. */
    private fun resolveTarget(event: AnActionEvent): PullRequestReviewCommentTarget? {
        val project = event.project ?: return null
        val editor = event.getData(CommonDataKeys.EDITOR) as? EditorEx ?: return null
        val virtualFile = editor.virtualFile ?: return null
        val pullRequestId = PullRequestCommentDecorationContext.resolvePullRequestId(project = project) ?: return null
        if (!PullRequestReviewCommentTarget.canOfferPullRequestLineComment(project = project, file = virtualFile, pullRequestId = pullRequestId)) {
            return null
        }
        val relativePath = PullRequestReviewCommentTarget.relativePathFor(project = project, file = virtualFile) ?: return null
        val editorLine = resolveEditorLine(event = event, editor = editor) ?: return null
        if (editorLine < 0 || editorLine >= editor.document.lineCount) {
            return null
        }
        if (ReviewInEditorUtil.isLastBlankLine(editor.document, editorLine)) {
            return null
        }
        return PullRequestReviewCommentTarget(
            project = project,
            editor = editor,
            relativePath = relativePath,
            pullRequestId = pullRequestId,
            fileOneBasedLine = editorLine + 1,
            side = resolveSide(project = project, editor = editor)
        )
    }

    /**
     * The gutter popup fires on the line number under the cursor, which the platform exposes through the
     * `active.line.number` client property on the gutter component; the editor text popup has no such property, so the
     * caret line is used (decision 7b).
     */
    private fun resolveEditorLine(event: AnActionEvent, editor: EditorEx): Int? {
        val activeLine = (editor.gutter as? EditorGutterComponentEx)?.getClientProperty("active.line.number") as? Int
        if (activeLine != null) {
            return activeLine
        }
        return editor.caretModel.currentCaret.logicalPosition.line.takeIf { line -> line >= 0 }
    }

    /**
     * A diff left editor comments against the pre-image (BEFORE / inline.from); the file-on-disk editor and the diff
     * right editor comment against the source (AFTER / inline.to). The service knows which registered diff viewer (if
     * any) owns [editor].
     */
    private fun resolveSide(project: Project, editor: EditorEx): InlineCommentSide {
        return BbCloudPullRequestDiffCommentService.getInstance(project).diffSideForEditor(editor = editor)
            ?: InlineCommentSide.AFTER
    }

    companion object {
        private val LOG = logger<PullRequestAddReviewCommentAction>()
    }
}