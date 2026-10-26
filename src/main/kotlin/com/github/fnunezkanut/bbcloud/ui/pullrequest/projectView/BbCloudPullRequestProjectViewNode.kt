package com.github.fnunezkanut.bbcloud.ui.pullrequest.projectView

import com.github.fnunezkanut.bbcloud.ui.components.BbCloudPluginIcons
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestVirtualFile
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * A leaf node in the Project View that represents the pull request a worktree project is reviewing, shown at the
 * worktree content root as `<prNum>.pr` with the same green file type icon as the editor tab.
 *
 * The node is backed by the same [BbCloudPullRequestVirtualFile] the editor opens, so navigating from the tree opens
 * (or focuses) the identical pull request editor. Closing the editor tab does not remove this node, because the node is
 * recomputed from the worktree folder name on every tree build rather than from any open tab.
 */
class BbCloudPullRequestProjectViewNode(project: Project, pullRequestFile: BbCloudPullRequestVirtualFile, settings: ViewSettings?) :
    ProjectViewNode<BbCloudPullRequestVirtualFile>(project, pullRequestFile, settings) {
    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        return emptyList()
    }

    override fun contains(file: VirtualFile): Boolean {
        return file == value
    }

    override fun getVirtualFile(): VirtualFile {
        return value
    }

    override fun update(presentation: PresentationData) {
        // 8a: the tree label is the file name only (`<prNum>.pr`), never the PR title, even when a summary is cached.
        presentation.presentableText = value.name
        presentation.setIcon(BbCloudPluginIcons.prIcon)
    }

    override fun isAlwaysLeaf(): Boolean {
        return true
    }

    override fun canNavigate(): Boolean {
        return true
    }

    override fun canNavigateToSource(): Boolean {
        return true
    }

    override fun navigate(requestFocus: Boolean) {
        val currentProject = project ?: return
        LOG.info("Opening pull request #${value.pullRequestPath.pullRequestId} from the Project View")
        FileEditorManager.getInstance(currentProject).openFile(value, requestFocus)
    }

    companion object {
        private val LOG = logger<BbCloudPullRequestProjectViewNode>()
    }
}