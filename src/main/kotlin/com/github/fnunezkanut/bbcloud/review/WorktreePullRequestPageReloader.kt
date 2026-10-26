package com.github.fnunezkanut.bbcloud.review

import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestReviewBarRefresh
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project

/**
 * The single entry point for reloading the whole pull request page of a work tree, callable from any editor tab.
 *
 * The `<prNum>.pr` editor session owns the rich panel reload (it re-fetches Bitbucket, rebinds the detail panel, and
 * preserves the selected tab), so when that editor is open the reloader simply delegates to the session's handler. The
 * worktree banner shows on every editor, though, including ones opened before the `.pr` editor was ever visited, so a
 * reload also has to work when no session handler is registered. In that case the reloader falls back to reloading the
 * [BbCloudWorktreePullRequestContext], which re-fetches the pull request and refreshes the editor notifications so the
 * banner's branch chips and refresh state stay in sync.
 *
 * The reloader drives [WorktreeTodoBannerViewModel.setRefreshEnabled] so the banner's compact refresh button disables
 * while a reload is in flight, mirroring what the session already does for the detail panel's refresh button.
 */
@Service(Service.Level.PROJECT)
class WorktreePullRequestPageReloader(private val project: Project) {
    /**
     * The session's full-panel reload, registered while the `<prNum>.pr` editor session is alive. The session also
     * manages the detail panel's own refresh-enabled state, so the handler is self-contained.
     */
    @Volatile
    private var sessionReloadHandler: (() -> Unit)? = null

    /** Registers the session's full-panel reload handler; the matching [unregisterSessionReloadHandler] clears it. */
    fun registerSessionReloadHandler(handler: () -> Unit) {
        sessionReloadHandler = handler
        LOG.debug("Registered the pull request page reload handler of the editor session")
    }

    /** Clears the session's reload handler, leaving the context-only fallback for later reloads. */
    fun unregisterSessionReloadHandler(handler: () -> Unit) {
        if (sessionReloadHandler === handler) {
            sessionReloadHandler = null
            LOG.debug("Unregistered the pull request page reload handler of the editor session")
        }
    }

    /**
     * Reloads the pull request page. Delegates to the session handler when the `.pr` editor is open; otherwise reloads
     * the work tree context so the banner refreshes without requiring an open detail panel.
     */
    fun requestReload() {
        val handler = sessionReloadHandler
        if (handler != null) {
            LOG.info("Reloading the pull request page through the editor session")
            handler()
            return
        }
        reloadContextOnly()
    }

    private fun reloadContextOnly() {
        if (!BbCloudPullRequestReviewBarRefresh.isPullRequestWorkTree(project.basePath)) {
            LOG.info("Skipping pull request page reload: the project is not a pull-request work tree")
            return
        }
        val bannerViewModel = WorktreeTodoBannerViewModel.getInstance(project)
        bannerViewModel.setRefreshEnabled(false)
        LOG.info("Reloading the pull request work tree context from the banner refresh")
        BbCloudWorktreePullRequestContext.getInstance(project).reload(reason = "banner refresh")
        // The context reload runs off the EDT and republishes the binding when it finishes, which refreshes the
        // banner. The refresh button is re-enabled once the banner picks the new binding up; until then a short
        // re-enable keeps the button usable even when the reload resolves to no change (same binding, no republish).
        bannerViewModel.setRefreshEnabled(true)
    }

    companion object {
        private val LOG = logger<WorktreePullRequestPageReloader>()

        fun getInstance(project: Project): WorktreePullRequestPageReloader {
            return project.service()
        }
    }
}