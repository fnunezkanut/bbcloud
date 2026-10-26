package com.github.fnunezkanut.bbcloud.ui.pullrequest.projectView

import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestReviewBarRefresh
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Refreshes the Project View once on project open for pull request worktree projects, so the persistent `<prNum>.pr`
 * entry contributed by [BbCloudPullRequestTreeStructureProvider] is built after the project is initialized.
 *
 * Ordinary projects are skipped entirely (the worktree check is a cheap folder-name parse), so normal repositories
 * never trigger an extra Project View refresh.
 */
class BbCloudPullRequestProjectViewRefreshStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(project.basePath)) {
            return
        }
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            LOG.debug("Refreshed Project View for pull request worktree entry in project ${project.locationHash}")
            ProjectView.getInstance(project).refresh()
        }
    }

    companion object {
        private val LOG = logger<BbCloudPullRequestProjectViewRefreshStartupActivity>()
    }
}