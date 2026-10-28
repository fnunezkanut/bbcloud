package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.github.fnunezkanut.bbcloud.git.FileLineCount
import com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext
import com.github.fnunezkanut.bbcloud.ui.diff.BbCloudPullRequestDiffCommentService
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestCommentFileOpener
import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.diff.editor.DiffViewerVirtualFile
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import java.util.WeakHashMap
import java.util.function.Function
import javax.swing.JComponent

/**
 * Shows the review bar at the top of the editor of a file the pull request changes.
 *
 * The bar only shows in a pull-request work tree, and only while the work tree is bound to an open pull request whose
 * HEAD is part of that pull request and which changes the open file. The binding comes from
 * [BbCloudWorktreePullRequestContext], so the bar follows the work tree and not the selected editor tab: it shows in
 * the normal text editor of the work tree as well as in the diff view.
 *
 * The kind of editor decides the action the bar offers: the diff of the file in a normal text editor, and the file
 * itself in a diff view.
 *
 * The provider is asked for the bar of every open file on every refresh, so the paths that do not qualify are
 * reported at the debug level: the info level would fill the log with the same line over and over.
 */
class BbCloudPullRequestReviewBarProvider : EditorNotificationProvider {
    /**
     * The bar of every editor the bar shows in.
     *
     * The platform asks the provider for the bar of an editor on every refresh, so the bar is built once per editor
     * and reused afterwards: a Compose tree is expensive to build, and the platform removes the bar of the previous
     * refresh before it asks for the new one.
     */
    private val bars = WeakHashMap<FileEditor, PullRequestReviewBar>()

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        // The diff views get the review bar inside the diff through PullRequestDiffBarsDiffExtension. The path of a diff
        // tab is its tab name, not the file it shows, so the path checks below cannot match a diff tab.
        if (file is DiffViewerVirtualFile) {
            LOG.debug("Review bar skipped: the diff view gets the review bar from the diff extension")
            return null
        }
        val projectBasePath = project.basePath
        if (projectBasePath == null) {
            LOG.debug("Review bar skipped: the project has no base path")
            return null
        }
        if (!BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(projectBasePath = projectBasePath)) {
            LOG.debug("Review bar skipped: the project is not a pull-request work tree")
            return null
        }
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        val binding = context.binding
        if (binding == null) {
            LOG.debug("Review bar skipped: no work tree pull request binding")
            return null
        }
        val relativePath = PullRequestPathMatcher.relativeProjectPath(
            projectBasePath = projectBasePath,
            absoluteOrRelativePath = file.path
        )
        if (!context.isFileChanged(relativePath)) {
            LOG.debug("Review bar skipped: '$relativePath' is not changed by the pull request of the work tree")
            return null
        }
        val pullRequestId = binding.pullRequestId
        val lineCount = context.fileLineCountFor(relativePath = relativePath)
        val revision = context.fileRevisionFor(relativePath = relativePath)
        return Function<FileEditor, JComponent?> { fileEditor ->
            buildBar(
                fileEditor = fileEditor,
                project = project,
                relativePath = relativePath,
                pullRequestId = pullRequestId,
                lineCount = lineCount,
                revision = revision
            )
        }
    }

    /**
     * Builds the bar of an editor.
     *
     * The bar shows in the source editors and in the diffs the IDE opens as an editor tab, but never in the pull
     * request editor of the plugin itself, which shows the pull request and not a file of the work tree.
     */
    private fun buildBar(
        fileEditor: FileEditor,
        project: Project,
        relativePath: String,
        pullRequestId: Long,
        lineCount: FileLineCount?,
        revision: String?
    ): JComponent? {
        return when (fileEditor) {
            is TextEditor -> {
                createBar(
                    relativePath = relativePath,
                    pullRequestId = pullRequestId,
                    lineCount = lineCount,
                    revision = revision,
                    surface = ReviewBarSurface.NormalFile,
                    action = showDiffAction(project = project, relativePath = relativePath),
                    project = project,
                    fileEditor = fileEditor,
                    onAddComment = addCommentAction(
                        project = project,
                        fileEditor = fileEditor,
                        relativePath = relativePath,
                        pullRequestId = pullRequestId
                    )
                )
            }
            is DiffEditorViewerFileEditor -> {
                createBar(
                    relativePath = relativePath,
                    pullRequestId = pullRequestId,
                    lineCount = lineCount,
                    revision = revision,
                    surface = ReviewBarSurface.Diff,
                    action = openFileAction(project = project, relativePath = relativePath),
                    project = project,
                    fileEditor = fileEditor,
                    onAddComment = addCommentAction(
                        project = project,
                        fileEditor = fileEditor,
                        relativePath = relativePath,
                        pullRequestId = pullRequestId
                    )
                )
            }
            else -> {
                LOG.debug("Review bar skipped: unsupported editor ${fileEditor.javaClass.simpleName}")
                null
            }
        }
    }

    /**
     * The action that opens the diff of the file.
     *
     * The file is part of the changes of the pull request, so the diff can always be opened; the lookup of the change
     * itself happens on the click, off the EDT.
     */
    private fun showDiffAction(project: Project, relativePath: String): ReviewBarAction {
        return ReviewBarAction { ReviewBarFileActions.showDiff(project = project, relativePath = relativePath) }
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

    private fun addCommentAction(project: Project, fileEditor: FileEditor, relativePath: String, pullRequestId: Long): () -> Unit {
        return {
            val editor = when (fileEditor) {
                is TextEditor -> fileEditor.editor as? EditorEx
                is DiffEditorViewerFileEditor -> when (val viewer = fileEditor.editorViewer) {
                    is TwosideTextDiffViewer -> viewer.getEditor(Side.RIGHT)
                    is DiffRequestProcessor -> (viewer.activeViewer as? TwosideTextDiffViewer)?.getEditor(Side.RIGHT)
                    else -> null
                }
                else -> null
            }
            if (editor == null) {
                LOG.info("Cannot open file comment composer for $relativePath: no text editor is available")
            } else {
                LOG.info("User clicked 'Add Comment' for $relativePath in pull request #$pullRequestId")
                BbCloudPullRequestDiffCommentService.getInstance(project).openFileScopedComposer(
                    editor = editor,
                    relativePath = relativePath,
                    pullRequestId = pullRequestId
                )
            }
        }
    }

    private fun createBar(
        relativePath: String,
        pullRequestId: Long,
        lineCount: FileLineCount?,
        revision: String?,
        surface: ReviewBarSurface,
        action: ReviewBarAction?,
        project: Project,
        fileEditor: FileEditor,
        onAddComment: () -> Unit
    ): JComponent {
        val existing = bars[fileEditor]
        if (existing != null &&
            existing.relativePath == relativePath &&
            existing.pullRequestId == pullRequestId &&
            existing.lineCount == lineCount &&
            existing.revision == revision &&
            existing.surface == surface
        ) {
            return existing.component
        }
        existing?.dispose()
        val bar = PullRequestReviewBar.create(
            relativePath = relativePath,
            pullRequestId = pullRequestId,
            lineCount = lineCount,
            revision = revision,
            surface = surface,
            action = action,
            onAddComment = onAddComment,
            project = project
        )
        bars[fileEditor] = bar
        if (!Disposer.tryRegister(fileEditor, bar)) {
            bar.dispose()
        }
        return bar.component
    }

    companion object {
        private val LOG = logger<BbCloudPullRequestReviewBarProvider>()
    }
}