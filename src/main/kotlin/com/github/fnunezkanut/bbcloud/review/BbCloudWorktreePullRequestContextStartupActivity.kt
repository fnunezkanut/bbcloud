package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestReviewBarRefresh
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Resolves the pull request of a pull-request work tree when the project is opened.
 *
 * The work tree folder carries the pull request number, so the project itself tells whether the work tree context is
 * needed at all. An ordinary project never pays for the resolution.
 */
class BbCloudWorktreePullRequestContextStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(project.basePath)) {
            return
        }
        LOG.debug("Resolving the work tree pull request context of the opened project")
        BbCloudWorktreePullRequestContext.getInstance(project).reload(reason = "project opened")
    }

    companion object {
        private val LOG = logger<BbCloudWorktreePullRequestContextStartupActivity>()
    }
}