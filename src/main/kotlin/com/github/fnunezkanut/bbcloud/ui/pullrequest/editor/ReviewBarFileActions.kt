package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestPathMatcher
import com.github.fnunezkanut.bbcloud.git.BbCloudPullRequestBranchDiffService
import com.github.fnunezkanut.bbcloud.git.BranchDiffResult
import com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestChangeDiffOpener
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestCommentFileOpener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangesUtil

/**
 * The actions the review bar offers on the files of the pull request.
 *
 * Both actions are shared by the two surfaces of the bar, so the way a file is opened does not depend on where the
 * bar is shown.
 */
internal object ReviewBarFileActions {
    /**
     * Opens the diff of the file against the branch the pull request is merged into.
     *
     * The lookup of the change is a blocking Git call, so it runs on a pooled thread; the diff itself is opened by
     * [PullRequestChangeDiffOpener], which marshals to the EDT.
     */
    fun showDiff(project: Project, relativePath: String) {
        LOG.info("User clicked 'Show Diff' for '$relativePath'")
        val binding = BbCloudWorktreePullRequestContext.getInstance(project).binding
        if (binding == null) {
            LOG.info("Cannot show the diff of '$relativePath': the work tree is not bound to a pull request")
            return
        }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = BbCloudPullRequestBranchDiffService().loadPullRequestChanges(
                    project = project,
                    destinationBranch = binding.destinationBranch,
                    sourceBranch = binding.sourceBranch
                )
                if (result !is BranchDiffResult.Success) {
                    LOG.info("Cannot show the diff of '$relativePath': the changes of the pull request could not be loaded")
                    return@executeOnPooledThread
                }
                val change = result.changes.firstOrNull { candidate ->
                    val candidatePath = PullRequestPathMatcher.relativeProjectPath(
                        projectBasePath = project.basePath,
                        absoluteOrRelativePath = ChangesUtil.getFilePath(candidate).path
                    )
                    candidatePath == relativePath
                }
                if (change == null) {
                    LOG.info("Cannot show the diff of '$relativePath': the file is not part of the changes of the pull request")
                    return@executeOnPooledThread
                }
                LOG.debug("Found the change of '$relativePath' among ${result.changes.size} change(s) of the pull request")
                PullRequestChangeDiffOpener.open(project = project, change = change)
            } catch (exception: ProcessCanceledException) {
                throw exception
            } catch (exception: Exception) {
                LOG.warn("Failed to show the diff of '$relativePath'", exception)
            }
        }
    }

    /**
     * Opens the file of the work tree in a normal editor.
     *
     * The file is resolved again on the click, so a file that disappeared since the bar was built is reported instead
     * of opening an editor that cannot be shown.
     */
    fun openFile(project: Project, relativePath: String) {
        LOG.info("User clicked 'Open File' for '$relativePath'")
        val file = PullRequestCommentFileOpener.resolve(project = project, inlinePath = relativePath)
        if (file == null) {
            LOG.info("Cannot open '$relativePath': the file is not in the work tree")
            return
        }
        ApplicationManager.getApplication().invokeLater {
            PullRequestCommentFileOpener.open(project = project, file = file, fileLine = null)
        }
    }

    private val LOG = logger<ReviewBarFileActions>()
}