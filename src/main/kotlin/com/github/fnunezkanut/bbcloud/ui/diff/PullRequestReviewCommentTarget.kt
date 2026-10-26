package com.github.fnunezkanut.bbcloud.ui.diff

import com.github.fnunezkanut.bbcloud.bitbucket.InlineCommentSide
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext
import com.github.fnunezkanut.bbcloud.review.PullRequestCommentDecorationContext
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestVirtualFile
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * A resolved, commentable target line in a pull request review editor.
 *
 * The companion [from] / [canOffer] logic is the single place that answers "can bbcloud offer a line-anchored review
 * comment here?". It is shared by the gutter controls model, the editor/gutter popup action and the attach path so the
 * green plus, the menu items and the attach gating all agree (plan decisions 3a, 4b, 6a, 9a).
 */
internal data class PullRequestReviewCommentTarget(
    val project: Project,
    val editor: EditorEx,
    val relativePath: String,
    val pullRequestId: Long,
    val fileOneBasedLine: Int,
    val side: InlineCommentSide
) {
    companion object {
        /**
         * Resolves whether a line-anchored review comment may be offered for [file] in [project].
         *
         * Returns true only when (4b) an inline comment index is loaded for the active pull request, (6a) the work tree
         * is bound to a reviewable (open) pull request, and (9a) [file] is a real work-tree file rather than the pull
         * request summary tab. File eligibility is otherwise any path under the project base path (3a), not limited to
         * the changed-files list.
         */
        fun canOfferPullRequestLineComment(project: Project, file: VirtualFile?, pullRequestId: Long): Boolean {
            return canOfferPullRequestLineComment(
                isPullRequestVirtualFile = file is BbCloudPullRequestVirtualFile,
                hasFile = file != null,
                isReviewable = isPullRequestReviewable(project = project, pullRequestId = pullRequestId),
                hasInlineIndex = PullRequestCommentDecorationContext.resolveInlineCommentIndex(
                    project = project,
                    pullRequestId = pullRequestId
                ) != null
            )
        }

        /**
         * The pure decision behind [canOfferPullRequestLineComment]: a line comment may be offered only for a real
         * work-tree file (9a) when the pull request is reviewable (6a) and an inline index is loaded (4b).
         */
        fun canOfferPullRequestLineComment(isPullRequestVirtualFile: Boolean, hasFile: Boolean, isReviewable: Boolean, hasInlineIndex: Boolean): Boolean {
            if (!hasFile || isPullRequestVirtualFile) {
                return false
            }
            if (!isReviewable) {
                return false
            }
            return hasInlineIndex
        }

        /**
         * The pull request is reviewable when the work tree is bound to it; a binding exists only for an open pull
         * request whose HEAD is part of it and whose changed files are known (decision 6a).
         */
        fun isPullRequestReviewable(project: Project, pullRequestId: Long): Boolean {
            return BbCloudWorktreePullRequestContext.getInstance(project).binding?.pullRequestId == pullRequestId
        }

        /** Resolves a repository-relative path for [file], or null when it falls outside the project base path. */
        fun relativePathFor(project: Project, file: VirtualFile): String? {
            val relativePath = PullRequestPathMatcher.relativeProjectPath(
                projectBasePath = project.basePath,
                absoluteOrRelativePath = file.path
            )
            return relativePath.takeIf { path -> path.isNotBlank() }
        }
    }
}