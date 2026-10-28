package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeProject
import com.github.fnunezkanut.bbcloud.review.WorktreeTodoBannerViewModel
import com.github.fnunezkanut.bbcloud.ui.pullrequest.create.BbCloudNewPullRequestVirtualFile
import com.intellij.diff.editor.DiffViewerVirtualFile
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import java.util.WeakHashMap
import java.util.function.Function
import javax.swing.JComponent

/**
 * Shows a full-width pull-request view blue bar under the editor tab and above the editor body for every file opened
 * in a plugin-managed pull request worktree whose pull request is proven Open.
 */
class BbCloudWorktreePrViewBlueBarEditorNotificationProvider :
    EditorNotificationProvider,
    DumbAware {
    private val bars = WeakHashMap<FileEditor, WorktreePrViewBlueBarPanel>()

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        // The create-pull-request form (new.pr) is not a worktree review tab, so never attach the worktree blue bar
        // above it even when the project itself is a plugin-managed worktree.
        if (file is BbCloudNewPullRequestVirtualFile) {
            LOG.debug("Blue bar skipped: the file is the new pull request form")
            return null
        }
        // The diff views get the blue bar inside the diff through PullRequestDiffBarsDiffExtension, so an editor
        // notification above the diff tab would show a second bar.
        if (file is DiffViewerVirtualFile) {
            LOG.debug("Blue bar skipped: the diff view gets the blue bar from the diff extension")
            return null
        }
        val projectBasePath = project.basePath
        if (projectBasePath == null) {
            LOG.debug("Blue bar skipped: the project has no base path")
            return null
        }
        val workTreeDirectory = BbCloudConfigurationService.getInstance().workTreeDirectoryOrNull()
        if (!PullRequestWorktreeProject.isPluginManagedWorktree(
                projectBasePath = projectBasePath,
                workTreeDirectory = workTreeDirectory
            )
        ) {
            LOG.debug("Blue bar skipped: the project is not a plugin-managed pull request worktree")
            return null
        }
        // Resolve the shared banner state synchronously (folder parse plus already-loaded context flags and the editor
        // virtual-file summary; no HTTP on the EDT) and show nothing unless the pull request is proven Open. This hides
        // the notification strip entirely on merged/declined pull requests and while the state is still unknown
        // (decisions 4a, 2b), rather than painting an empty row.
        val viewModel = WorktreeTodoBannerViewModel.getInstance(project)
        viewModel.ensureInitialized()
        if (!viewModel.state.value.isWorktree) {
            LOG.debug("Blue bar skipped: the pull request of the work tree is not proven open")
            return null
        }
        return Function<FileEditor, JComponent?> { fileEditor ->
            barFor(project = project, fileEditor = fileEditor)
        }
    }

    private fun barFor(project: Project, fileEditor: FileEditor): JComponent {
        val existing = bars[fileEditor]
        if (existing != null) {
            return existing
        }
        val bar = WorktreePrViewBlueBarPanel.create(project = project)
        bars[fileEditor] = bar
        if (!Disposer.tryRegister(fileEditor, bar)) {
            bar.dispose()
        }
        return bar
    }

    companion object {
        private val LOG = logger<BbCloudWorktreePrViewBlueBarEditorNotificationProvider>()
    }
}