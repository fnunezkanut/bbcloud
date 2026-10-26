package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryUrls
import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.project.LocalRepositoryProjectOpener
import com.github.fnunezkanut.bbcloud.review.WorktreePullRequestPageReloader
import com.github.fnunezkanut.bbcloud.review.WorktreeTodoBannerUiState
import com.github.fnunezkanut.bbcloud.ui.pullrequest.projectView.BbCloudPullRequestVirtualFiles
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.nio.file.Path

/**
 * The click handlers of the worktree todo banner header, shared by every editor's banner through the project services
 * the banner reads. Kept as a small object so the banner's Compose factory stays declarative.
 */
internal object WorktreeTodoBannerActions {
    private val LOG = logger<WorktreeTodoBannerActions>()

    /**
     * Opens the main local checkout of the repository, mirroring the pull request information card: the local project
     * is opened or focused when a checkout exists under the configured projects directory, otherwise the repository is
     * opened on the Bitbucket web page.
     */
    fun openMainProject(project: Project, state: WorktreeTodoBannerUiState, coroutineScope: CoroutineScope) {
        val repoSlug = state.repoSlug
        val pullRequestId = state.pullRequestId
        if (repoSlug.isBlank()) {
            LOG.info("Cannot open the repository locally from the worktree banner for pull request #$pullRequestId: slug is unavailable")
            browseRepository(project = project, repoSlug = repoSlug, pullRequestId = pullRequestId)
            return
        }
        LOG.info("User clicked the repository slug $repoSlug on the worktree banner for pull request #$pullRequestId")
        val localPath = BbCloudConfigurationService.getInstance().localCheckoutPathForRepo(repoSlug)
        if (localPath.isBlank()) {
            LOG.info("No local checkout for $repoSlug; opening the repository for pull request #$pullRequestId on the web")
            browseRepository(project = project, repoSlug = repoSlug, pullRequestId = pullRequestId)
            return
        }
        coroutineScope.launch {
            val opened = LocalRepositoryProjectOpener.openOrFocus(
                repoSlug = repoSlug,
                checkoutPath = Path.of(localPath)
            )
            if (opened != null) {
                LOG.info("Opened local project $repoSlug from the worktree banner")
            } else {
                LOG.warn("Could not open local project $repoSlug from the worktree banner; falling back to the Bitbucket web page")
                ApplicationManager.getApplication().invokeLater {
                    browseRepository(project = project, repoSlug = repoSlug, pullRequestId = pullRequestId)
                }
            }
        }
    }

    /** Opens the pull request on Bitbucket, preferring the known web URL and otherwise building it from the org URL. */
    fun openPullRequest(project: Project, state: WorktreeTodoBannerUiState) {
        val pullRequestId = state.pullRequestId
        val url = state.pullRequestWebUrl
        if (url.isNullOrBlank()) {
            LOG.info("Pull request link unavailable for pull request #$pullRequestId on the worktree banner")
            return
        }
        LOG.info("User clicked pr#$pullRequestId on the worktree banner; opening it in the browser")
        BrowserUtil.browse(url)
    }

    /**
     * Opens IntelliJ's left-docked Version Control changes browser with the pull request's branch diff, matching the
     * plugin's own Changes tab file set. No-op on non-worktrees, and returns early (logging at `info`) when the branch
     * names have not resolved yet, so the button's disabled state and this guard agree.
     */
    fun openChangedFiles(project: Project, state: WorktreeTodoBannerUiState) {
        if (!state.isWorktree) {
            return
        }
        val destinationBranch = state.destinationBranch
        val sourceBranch = state.sourceBranch
        if (destinationBranch.isNullOrBlank() || sourceBranch.isNullOrBlank()) {
            LOG.info(
                "Cannot show changed files for pull request #${state.pullRequestId} in repository ${state.repoSlug}: " +
                    "branch names not resolved yet"
            )
            return
        }
        LOG.info(
            "User clicked show changed files on the worktree banner for pull request #${state.pullRequestId} in " +
                "repository ${state.repoSlug}"
        )
        WorktreePullRequestChangedFilesToolWindowOpener.open(
            project = project,
            destinationBranch = destinationBranch,
            sourceBranch = sourceBranch
        )
    }

    /**
     * Opens or focuses the pull request editor page for the worktree's pull request. No-op when the project is not a
     * pull request worktree or the backing editor virtual file cannot be resolved.
     */
    fun openPrPage(project: Project, state: WorktreeTodoBannerUiState) {
        if (!state.isWorktree) {
            LOG.info("Cannot open the pull request page for pull request #${state.pullRequestId}: project is not a worktree")
            return
        }
        val file = BbCloudPullRequestVirtualFiles.forWorktreeProject(project)
        if (file == null) {
            LOG.info(
                "Cannot open the pull request page for pull request #${state.pullRequestId}: " +
                    "the editor virtual file is unavailable"
            )
            return
        }
        LOG.info(
            "User clicked open PR page on the worktree banner for pull request #${state.pullRequestId} in " +
                "repository ${state.repoSlug}"
        )
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    /** Reloads the pull request page through the shared reloader. */
    fun refresh(project: Project, state: WorktreeTodoBannerUiState) {
        LOG.info("User clicked refresh on the worktree banner for pull request #${state.pullRequestId} in repository ${state.repoSlug}")
        WorktreePullRequestPageReloader.getInstance(project).requestReload()
    }

    private fun browseRepository(project: Project, repoSlug: String, pullRequestId: Long) {
        val organizationUrl = BbCloudConfigurationService.getInstance().organizationUrlOrNull()
        val url = organizationUrl?.let { BitbucketRepositoryUrls.repositoryWebUrl(organizationUrl = it, repoSlug = repoSlug) }
        if (url.isNullOrBlank()) {
            LOG.info("Repository link unavailable for pull request #$pullRequestId on the worktree banner")
            return
        }
        BrowserUtil.browse(url)
    }
}