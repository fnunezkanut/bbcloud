package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.ui.pullrequest.changes.WorktreePullRequestChangesToolWindowFactory
import com.github.fnunezkanut.bbcloud.ui.pullrequest.changes.WorktreePullRequestChangesToolWindowService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.annotations.RequiresEdt

/**
 * Shows and activates the plugin's PR Changes tool window (tool window id
 * [WorktreePullRequestChangesToolWindowFactory.TOOL_WINDOW_ID]) for the pull request's three-dot branch diff
 * (`destination...source`), the same file set the plugin's review bar uses.
 *
 * The tool window hosts the Compose file tree of the changed files; the Git diff runs off the EDT inside the tool
 * window service's reload, so the click handler stays synchronous on the EDT.
 */
internal object WorktreePullRequestChangedFilesToolWindowOpener {
    private val LOG = logger<WorktreePullRequestChangedFilesToolWindowOpener>()

    /**
     * Shows the PR Changes tool window for the pull request diff between [destinationBranch] (merge target) and
     * [sourceBranch] (the checked-out pull request branch). Must be called on the EDT; returns without opening
     * anything when [project] is already disposed or the tool window is not available (for example not a work tree).
     */
    @RequiresEdt
    fun open(project: Project, pullRequestId: Long?, destinationBranch: String, sourceBranch: String) {
        if (project.isDisposed) {
            LOG.info("Not opening the changed files tool window: project ${project.name} is disposed")
            return
        }
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(WorktreePullRequestChangesToolWindowFactory.TOOL_WINDOW_ID)
        if (toolWindow == null) {
            LOG.info("Not opening the changed files tool window: it is not available for project ${project.name}")
            return
        }
        toolWindow.activate {
            WorktreePullRequestChangesToolWindowService.getInstance(project).showFor(
                pullRequestId = pullRequestId,
                destinationBranch = destinationBranch,
                sourceBranch = sourceBranch
            )
        }
    }
}