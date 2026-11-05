@file:Suppress("ktlint:standard:function-naming")

package com.github.fnunezkanut.bbcloud.ui.pullrequest.changes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext
import com.github.fnunezkanut.bbcloud.toolwindow.BbCloudToolWindowIdLabel
import com.github.fnunezkanut.bbcloud.ui.main.PullRequestChangesScreen
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestReviewBarRefresh
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.jewel.bridge.addComposeTab

/**
 * Registers the plugin's PR Changes tool window, a left-docked panel that hosts the Compose file tree of the pull request's
 * changed files. It is available only in pull-request work-tree projects (via [isApplicableAsync]), so an ordinary
 * project never pays for it.
 */
class WorktreePullRequestChangesToolWindowFactory :
    ToolWindowFactory,
    DumbAware {
    override suspend fun isApplicableAsync(project: Project): Boolean {
        return BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(projectBasePath = project.basePath)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        LOG.debug("Creating the PR Changes tool window content for project ${project.name}")
        // Must run before addComposeTab: the platform evaluates the property only while rebuilding the header, so setting
        // it later leaves the tool window name in the header until an unrelated event rebuilds it.
        BbCloudToolWindowIdLabel.hide(toolWindow.component)
        val service = WorktreePullRequestChangesToolWindowService.getInstance(project)
        // Resolve the context outside composition: if it were first created inside the composition snapshot, its
        // Compose state objects would belong to that unapplied snapshot and reading them would throw.
        val context = BbCloudWorktreePullRequestContext.getInstance(project)
        toolWindow.addComposeTab(
            tabDisplayName = BbCloudBundle.message("worktree.changesToolWindow.tabTitle.fallback"),
            isLockable = false,
            isCloseable = false
        ) {
            PullRequestChangesScreen(filesViewModel = service.viewModel)
            CommentCountsEffect(context = context, service = service)
        }
        // If the tool window is opened from its stripe button (not from the blue bar) and no branches are known yet,
        // seed it from the current binding, so the tree shows the files right away.
        val binding = context.binding
        if (binding != null) {
            service.showFor(
                pullRequestId = binding.pullRequestId,
                destinationBranch = binding.destinationBranch,
                sourceBranch = binding.sourceBranch
            )
        } else if (context.pullRequestKnownClosed) {
            service.viewModel.showResolved(pullRequestId = null)
        }
    }

    @Composable
    private fun CommentCountsEffect(context: BbCloudWorktreePullRequestContext, service: WorktreePullRequestChangesToolWindowService) {
        val revision by context.commentsRevision
        LaunchedEffect(revision) {
            service.applyCommentCounts()
        }
        val statusRevision by context.pullRequestStatusRevision
        LaunchedEffect(statusRevision) {
            service.onPullRequestStatusChanged()
        }
    }

    companion object {
        const val TOOL_WINDOW_ID = "Bitbucket Cloud PR Changes"

        private val LOG = logger<WorktreePullRequestChangesToolWindowFactory>()
    }
}