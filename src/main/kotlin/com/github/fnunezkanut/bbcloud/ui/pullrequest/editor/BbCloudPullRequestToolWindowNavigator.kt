package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.ui.BbCloudToolWindowPanel
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/** Selects the Bitbucket Cloud PR Review Project - Open PRs listing from an editor-owned project link. */
class BbCloudPullRequestToolWindowNavigator(private val project: Project) {
    fun showProjectOpenPullRequests() {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        if (toolWindow == null) {
            LOG.info("Cannot navigate to Project - Open PRs: Bitbucket Cloud PR Review tool window is unavailable")
            return
        }
        LOG.info("User navigated from a pull request editor to Project - Open PRs")
        val panel = project.service<BbCloudToolWindowPanel>()
        toolWindow.show {
            if (!panel.showProjectOpenPullRequestsListing()) {
                LOG.info("Cannot navigate to Project - Open PRs: listing content is unavailable")
            }
        }
    }

    companion object {
        private val LOG = logger<BbCloudPullRequestToolWindowNavigator>()
        private const val TOOL_WINDOW_ID = "Bitbucket Cloud PR Review"
    }
}