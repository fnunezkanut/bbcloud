package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.github.fnunezkanut.bbcloud.git.FileLineCount
import com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext
import com.github.fnunezkanut.bbcloud.ui.diff.BbCloudPullRequestDiffCommentService
import com.github.fnunezkanut.bbcloud.ui.diff.PullRequestReviewCommentTarget
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestCommentFileOpener
import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.util.DiffNotificationProvider
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import javax.swing.JComponent

/**
 * Adds the pull-request blue bar and the review bar to every diff viewer of a pull-request worktree.
 *
 * The platform creates a diff viewer for every diff it shows: the diffs opened as an editor tab, the diff chains opened
 * from the PR Changes tool window, and the diffs the plugin opens in a diff window. Attaching the bars here
 * covers all of them, and the viewer that the platform creates on every navigation to another file of a chain gets the
 * bar of that file. The editor notification providers skip the diff tabs so the bars do not appear twice.
 *
 * The bars are added to the request once per kind, because the platform renders the providers of a request for every
 * viewer it creates. The blue bar is added first, so it renders above the review bar.
 *
 * The diff review bar also offers previous and next unviewed-file navigation within the diff chain, in chain order.
 * Checking "Viewed" in the diff review bar moves the chain to the next unviewed file.
 *
 * "Add Comment" opens a comment composer in the diff editor. For a file the pull request deletes (whose path no longer
 * exists on the source branch) it opens a pull-request-level composer; for an added file and in the unified diff view
 * it opens the file comment composer.
 */
class PullRequestDiffBarsDiffExtension : DiffExtension() {
    override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
        val project = context.project ?: return
        if (request.getUserData(BLUE_BAR_ATTACHED_KEY) != true) {
            request.putUserData(BLUE_BAR_ATTACHED_KEY, true)
            DiffUtil.addNotification(
                DiffNotificationProvider { diffViewer -> WorktreePrViewBlueBarDiffNotification.create(project = project, viewer = diffViewer) },
                request
            )
        }
        if (request.getUserData(REVIEW_BAR_ATTACHED_KEY) != true) {
            val provider = reviewBarProviderFor(project = project, request = request, diffContext = context) ?: return
            request.putUserData(REVIEW_BAR_ATTACHED_KEY, true)
            DiffUtil.addNotification(provider, request)
        }
    }

    /**
     * The provider of the review bar of the file of [request], or null when the bar does not apply.
     *
     * The bar only shows in a pull-request work tree, and only for a file the pull request changes. The file comes from
     * the change of the request; a request without a change (for example a diff of two arbitrary files) gets no review bar.
     *
     * The [diffContext] is the context of the diff that shows the request. The bar uses it to find the chain of that diff,
     * so it can offer the previous and next unviewed-file buttons.
     */
    private fun reviewBarProviderFor(project: Project, request: DiffRequest, diffContext: DiffContext): DiffNotificationProvider? {
        val projectBasePath = project.basePath ?: return null
        if (!BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(projectBasePath = projectBasePath)) {
            return null
        }
        val change = request.getUserData(ChangeDiffRequestProducer.CHANGE_KEY)
        if (change == null) {
            LOG.debug("Review bar of diff skipped: the diff request has no change")
            return null
        }
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        val binding = context.binding
        if (binding == null) {
            LOG.debug("Review bar of diff skipped: no work tree pull request binding")
            return null
        }
        val relativePath = PullRequestPathMatcher.relativeProjectPath(
            projectBasePath = projectBasePath,
            absoluteOrRelativePath = ChangesUtil.getFilePath(change).path
        )
        if (!context.isFileChanged(relativePath)) {
            LOG.debug("Review bar of diff skipped: '$relativePath' is not changed by the pull request of the work tree")
            return null
        }
        val pullRequestId = binding.pullRequestId
        val lineCount = context.fileLineCountFor(relativePath = relativePath)
        val revision = context.fileRevisionFor(relativePath = relativePath)
        val changeType = change.type
        return DiffNotificationProvider { viewer ->
            createReviewBar(
                project = project,
                viewer = viewer,
                diffContext = diffContext,
                relativePath = relativePath,
                pullRequestId = pullRequestId,
                lineCount = lineCount,
                revision = revision,
                changeType = changeType
            )
        }
    }

    private fun createReviewBar(
        project: Project,
        viewer: FrameDiffTool.DiffViewer?,
        diffContext: DiffContext,
        relativePath: String,
        pullRequestId: Long,
        lineCount: FileLineCount?,
        revision: String?,
        changeType: Change.Type?
    ): JComponent? {
        val commentScope = ReviewBarCommentScopes.scopeFor(
            changeType = changeType,
            onesideSide = ReviewBarDiffEditors.onesideSideOf(viewer = viewer)
        )
        val addCommentTooltip = if (commentScope == ReviewBarCommentScope.PullRequestLevel) {
            BbCloudBundle.message("editor.reviewBar.addComment.deletedFile.tooltip")
        } else {
            null
        }
        val bar = PullRequestReviewBar.create(
            relativePath = relativePath,
            pullRequestId = pullRequestId,
            lineCount = lineCount,
            revision = revision,
            surface = ReviewBarSurface.Diff,
            action = openFileAction(project = project, relativePath = relativePath),
            onAddComment = addCommentAction(
                project = project,
                viewer = viewer,
                relativePath = relativePath,
                pullRequestId = pullRequestId,
                commentScope = commentScope
            ),
            project = project,
            navigator = ReviewBarDiffChainNavigator(project = project, diffContext = diffContext, viewer = viewer),
            addCommentTooltip = addCommentTooltip
        )
        if (viewer == null || !Disposer.tryRegister(viewer, bar)) {
            bar.dispose()
        }
        return bar.component
    }

    private fun addCommentAction(
        project: Project,
        viewer: FrameDiffTool.DiffViewer?,
        relativePath: String,
        pullRequestId: Long,
        commentScope: ReviewBarCommentScope
    ): () -> Unit {
        return {
            LOG.info("User clicked 'Add Comment' for '$relativePath' ($commentScope) in pull request #$pullRequestId")
            if (!PullRequestReviewCommentTarget.isPullRequestReviewable(project = project, pullRequestId = pullRequestId)) {
                LOG.info(
                    "Cannot open comment composer for '$relativePath': the work tree is no longer bound to pull request #$pullRequestId"
                )
            } else {
                val editor = ReviewBarDiffEditors.commentEditorFor(viewer = viewer)
                if (editor == null) {
                    LOG.info(
                        "Cannot open comment composer for '$relativePath' in pull request #$pullRequestId: no text editor is available in the diff"
                    )
                } else {
                    val diffCommentService = BbCloudPullRequestDiffCommentService.getInstance(project)
                    when (commentScope) {
                        ReviewBarCommentScope.FileScoped -> diffCommentService.openFileScopedComposer(
                            editor = editor,
                            relativePath = relativePath,
                            pullRequestId = pullRequestId
                        )
                        ReviewBarCommentScope.PullRequestLevel -> diffCommentService.openPullRequestLevelComposer(
                            editor = editor,
                            relativePath = relativePath,
                            pullRequestId = pullRequestId
                        )
                    }
                }
            }
        }
    }

    /**
     * The action that opens the file in a normal editor, or null when the file is not in the work tree.
     *
     * A file the pull request deletes has no working-tree file to open, so the bar offers no button at all instead of
     * one that does nothing.
     */
    private fun openFileAction(project: Project, relativePath: String): ReviewBarAction? {
        val file = PullRequestCommentFileOpener.resolve(project = project, inlinePath = relativePath)
        if (file == null) {
            LOG.debug("Review bar of '$relativePath' offers no 'Open File' action: the file is not in the work tree")
            return null
        }
        return ReviewBarAction { ReviewBarFileActions.openFile(project = project, relativePath = relativePath) }
    }

    companion object {
        private val LOG = logger<PullRequestDiffBarsDiffExtension>()
        private val BLUE_BAR_ATTACHED_KEY = Key.create<Boolean>("BbCloud.PullRequestDiffBarsDiffExtension.BlueBarAttached")
        private val REVIEW_BAR_ATTACHED_KEY = Key.create<Boolean>("BbCloud.PullRequestDiffBarsDiffExtension.ReviewBarAttached")
    }
}