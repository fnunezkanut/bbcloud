package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.review.WorktreePullRequestPageReloader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/**
 * Opens or selects the `<prNum>.pr` editor page after a pull-request-level comment was added from a deleted file, and
 * scrolls its left pane to the newest activity so the new comment is visible.
 *
 * An already-open page is selected and then reloaded with the reveal flag so it re-fetches and scrolls to the top. A
 * freshly opened page needs no reload: its first load already includes the new comment and forces the scroll. When the
 * page cannot be opened the work tree context is reloaded instead, so the banner and comment counts still refresh.
 */
@Service(Service.Level.PROJECT)
class BbCloudPullRequestPageRevealer(private val project: Project) {
    fun revealNewestActivity(pullRequestId: Long) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            LOG.info("Revealing the newest activity of pull request #$pullRequestId after adding a comment from a deleted file")
            when (project.service<BbCloudPullRequestEditorOpener>().openById(pullRequestId = pullRequestId)) {
                PullRequestPageOpenResult.SelectedExisting -> {
                    WorktreePullRequestPageReloader.getInstance(project).requestReload(revealNewestActivity = true)
                }
                PullRequestPageOpenResult.OpenedNew -> {
                    // The first load of the freshly opened page already includes the new comment and scrolls to the top.
                }
                PullRequestPageOpenResult.Failed -> {
                    WorktreePullRequestPageReloader.getInstance(project).requestReload()
                    LOG.info("Could not open the page of pull request #$pullRequestId after adding a comment")
                }
            }
        }
    }

    companion object {
        private val LOG = logger<BbCloudPullRequestPageRevealer>()

        fun getInstance(project: Project): BbCloudPullRequestPageRevealer {
            return project.service()
        }
    }
}