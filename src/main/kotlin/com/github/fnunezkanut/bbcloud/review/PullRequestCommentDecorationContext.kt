package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestInlineCommentIndex
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestVirtualFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

/**
 * Resolves which pull request and inline comment index should drive editor/diff decoration.
 *
 * The review bar follows [BbCloudWorktreePullRequestContext.binding]; decoration must not depend only on the selected
 * editor tab, because reviewers often keep the PR tab open while editing files in the work tree.
 */
internal object PullRequestCommentDecorationContext {
    fun resolvePullRequestId(project: Project): Long? {
        val reviewState = BbCloudPullRequestReviewState.getInstance(project)
        return resolvePullRequestId(
            activeReviewPullRequestId = reviewState.activePullRequestId,
            openPullRequestTabId = openPullRequestTabId(project = project),
            worktreeBindingPullRequestId = BbCloudWorktreePullRequestContext.getInstance(project).binding?.pullRequestId
        )
    }

    fun resolvePullRequestId(activeReviewPullRequestId: Long?, openPullRequestTabId: Long?, worktreeBindingPullRequestId: Long?): Long? {
        activeReviewPullRequestId?.let { pullRequestId -> return pullRequestId }
        openPullRequestTabId?.let { pullRequestId -> return pullRequestId }
        return worktreeBindingPullRequestId
    }

    fun resolveInlineCommentIndex(project: Project, pullRequestId: Long, requestReloadWhenEmpty: Boolean = true): PullRequestInlineCommentIndex? {
        val reviewState = BbCloudPullRequestReviewState.getInstance(project)
        val worktreeContext = BbCloudWorktreePullRequestContext.getInstance(project)
        val index = resolveInlineCommentIndex(
            pullRequestId = pullRequestId,
            reviewStatePullRequestId = reviewState.activePullRequestId,
            reviewStateIndex = reviewState.inlineCommentIndex,
            bindingPullRequestId = worktreeContext.binding?.pullRequestId,
            comments = worktreeContext.comments
        )
        if (index != null) {
            return index
        }
        if (requestReloadWhenEmpty && worktreeContext.binding?.pullRequestId == pullRequestId) {
            worktreeContext.requestCommentsReload(force = false)
        }
        return null
    }

    fun resolveInlineCommentIndex(
        pullRequestId: Long,
        reviewStatePullRequestId: Long?,
        reviewStateIndex: PullRequestInlineCommentIndex?,
        bindingPullRequestId: Long?,
        comments: List<PullRequestComment>
    ): PullRequestInlineCommentIndex? {
        if (reviewStatePullRequestId == pullRequestId) {
            reviewStateIndex?.let { index -> return index }
        }
        if (bindingPullRequestId != pullRequestId) {
            return null
        }
        if (comments.isNotEmpty()) {
            return PullRequestInlineCommentIndex.from(comments = comments)
        }
        return null
    }

    /** Writes the worktree comment snapshot into [BbCloudPullRequestReviewState] when it matches [pullRequestId]. */
    fun syncInlineIndexFromWorktree(project: Project, pullRequestId: Long, requestReloadWhenEmpty: Boolean = true): Boolean {
        val reviewState = BbCloudPullRequestReviewState.getInstance(project)
        if (reviewState.activePullRequestId != pullRequestId) {
            reviewState.bindPullRequest(pullRequestId = pullRequestId)
        }
        val worktreeContext = BbCloudWorktreePullRequestContext.getInstance(project)
        if (worktreeContext.binding?.pullRequestId != pullRequestId) {
            return false
        }
        if (worktreeContext.comments.isEmpty()) {
            if (requestReloadWhenEmpty) {
                worktreeContext.requestCommentsReload(force = false)
            }
            return false
        }
        return reviewState.updateInlineComments(
            pullRequestId = pullRequestId,
            index = PullRequestInlineCommentIndex.from(comments = worktreeContext.comments)
        )
    }

    fun openPullRequestTabId(project: Project): Long? {
        val fileManager = FileEditorManager.getInstance(project)
        return fileManager.openFiles
            .filterIsInstance<BbCloudPullRequestVirtualFile>()
            .firstOrNull()
            ?.pullRequestPath
            ?.pullRequestId
    }
}