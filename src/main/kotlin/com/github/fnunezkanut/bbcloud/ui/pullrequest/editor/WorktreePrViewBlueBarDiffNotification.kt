package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeProject
import com.intellij.diff.FrameDiffTool
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

/**
 * Creates the pull-request view blue bar of a diff view.
 *
 * The bar is created for every diff viewer of a plugin-managed pull request worktree, because the bar describes the
 * pull request and not the file of the diff. The panel reads the shared banner state, so it renders nothing while the
 * pull request is not proven Open, including when the state resolves after the diff was opened.
 */
internal object WorktreePrViewBlueBarDiffNotification {
    /**
     * Returns the blue bar of [viewer], or null when the project is not a plugin-managed pull request worktree or the
     * viewer is gone. The panel is disposed with the viewer.
     */
    fun create(project: Project, viewer: FrameDiffTool.DiffViewer?): JComponent? {
        val projectBasePath = project.basePath
        val workTreeDirectory = BbCloudConfigurationService.getInstance().workTreeDirectoryOrNull()
        if (!PullRequestWorktreeProject.isPluginManagedWorktree(
                projectBasePath = projectBasePath,
                workTreeDirectory = workTreeDirectory
            )
        ) {
            LOG.debug("Blue bar of diff skipped: the project is not a plugin-managed pull request worktree")
            return null
        }
        if (viewer == null) {
            LOG.debug("Blue bar of diff skipped: the diff has no viewer")
            return null
        }
        val bar = WorktreePrViewBlueBarPanel.create(project = project)
        if (!Disposer.tryRegister(viewer, bar)) {
            LOG.debug("Blue bar of diff skipped: the diff viewer is already disposed")
            bar.dispose()
            return null
        }
        LOG.debug("Attached the blue bar to a diff viewer")
        return bar
    }

    private val LOG = logger<WorktreePrViewBlueBarDiffNotification>()
}