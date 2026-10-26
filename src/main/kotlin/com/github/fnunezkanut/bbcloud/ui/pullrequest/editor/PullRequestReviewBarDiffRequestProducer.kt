package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.github.fnunezkanut.bbcloud.git.FileLineCount
import com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext
import com.github.fnunezkanut.bbcloud.ui.diff.BbCloudPullRequestDiffCommentService
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestCommentFileOpener
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffNotificationProvider
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import javax.swing.JComponent

/**
 * Adds the review bar to a diff the plugin opens outside an editor tab.
 *
 * The diffs the IDE opens as an editor tab get the bar from the editor notification provider, but the diffs the plugin
 * opens in a diff window need the bar attached to the diff request, which the platform renders at the top of the diff.
 *
 * The bar of a diff always offers to open the file in a normal editor, and only when the file is in the work tree.
 */
internal class PullRequestReviewBarDiffRequestProducer(
    private val project: Project,
    private val delegate: ChangeDiffRequestChain.Producer,
    private val relativePath: String,
    private val pullRequestId: Long,
    private val lineCount: FileLineCount?,
    private val revision: String?
) : ChangeDiffRequestChain.Producer {
    override fun getName(): String {
        return delegate.name
    }

    override fun getFilePath(): FilePath {
        return delegate.filePath
    }

    override fun getFileStatus(): FileStatus {
        return delegate.fileStatus
    }

    override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
        val request = delegate.process(context, indicator)
        DiffUtil.addNotification(
            DiffNotificationProvider { viewer -> createBar(viewer = viewer) },
            request
        )
        return request
    }

    private fun createBar(viewer: FrameDiffTool.DiffViewer?): JComponent {
        val bar = PullRequestReviewBar.create(
            relativePath = relativePath,
            pullRequestId = pullRequestId,
            lineCount = lineCount,
            revision = revision,
            surface = ReviewBarSurface.Diff,
            action = openFileAction(),
            onAddComment = addCommentAction(viewer = viewer),
            project = project
        )
        if (viewer == null || !Disposer.tryRegister(viewer, bar)) {
            bar.dispose()
        }
        return bar.component
    }

    private fun addCommentAction(viewer: FrameDiffTool.DiffViewer?): () -> Unit {
        return {
            val editor = when (viewer) {
                is TwosideTextDiffViewer -> viewer.getEditor(Side.RIGHT)
                is DiffRequestProcessor -> (viewer.activeViewer as? TwosideTextDiffViewer)?.getEditor(Side.RIGHT)
                else -> null
            }
            if (editor != null) {
                BbCloudPullRequestDiffCommentService.getInstance(project).openFileScopedComposer(
                    editor = editor,
                    relativePath = relativePath,
                    pullRequestId = pullRequestId
                )
            }
        }
    }

    /**
     * The action that opens the file in a normal editor, or null when the file is not in the work tree.
     *
     * A file the pull request deletes has no working-tree file to open, so the bar offers no button at all instead of
     * one that does nothing.
     */
    private fun openFileAction(): ReviewBarAction? {
        val file = PullRequestCommentFileOpener.resolve(project = project, inlinePath = relativePath)
        if (file == null) {
            LOG.debug("Review bar of '$relativePath' offers no 'Open File' action: the file is not in the work tree")
            return null
        }
        return ReviewBarAction { ReviewBarFileActions.openFile(project = project, relativePath = relativePath) }
    }

    companion object {
        private val LOG = logger<PullRequestReviewBarDiffRequestProducer>()

        /**
         * Wraps the producer of a diff with the review bar, when the bar belongs to the file of the diff.
         *
         * The producer is returned unchanged when the bar does not apply, so the diff opens exactly as it did before.
         * The bar follows the pull request of the work tree, so a diff of a project that is not a pull-request work
         * tree never shows one.
         */
        fun wrap(project: Project, change: Change, delegate: ChangeDiffRequestChain.Producer): ChangeDiffRequestChain.Producer {
            val projectBasePath = project.basePath ?: return delegate
            if (!BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(projectBasePath = projectBasePath)) {
                return delegate
            }
            val context = BbCloudWorktreePullRequestContext.getInstance(project)
            val binding = context.binding ?: return delegate
            val relativePath = PullRequestPathMatcher.relativeProjectPath(
                projectBasePath = projectBasePath,
                absoluteOrRelativePath = ChangesUtil.getFilePath(change).path
            )
            if (!context.isFileChanged(relativePath)) {
                return delegate
            }
            return PullRequestReviewBarDiffRequestProducer(
                project = project,
                delegate = delegate,
                relativePath = relativePath,
                pullRequestId = binding.pullRequestId,
                lineCount = context.fileLineCountFor(relativePath = relativePath),
                revision = context.fileRevisionFor(relativePath = relativePath)
            )
        }
    }
}