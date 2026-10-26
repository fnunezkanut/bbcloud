package com.github.fnunezkanut.bbcloud.ui.pullrequest.projectView

import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestVirtualFile
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

/**
 * Injects a persistent `<prNum>.pr` leaf node into the default Project View file tree, at the content root of a pull
 * request worktree project, so the pull request editor always has a visible entry point even after its tab is closed.
 *
 * The provider is a no-op for ordinary (non-worktree) projects, so normal repositories pay no cost beyond the cheap
 * worktree folder-name check performed by [BbCloudPullRequestVirtualFiles.forWorktreeProject].
 */
class BbCloudPullRequestTreeStructureProvider(private val project: Project) :
    TreeStructureProvider,
    DumbAware {
    override fun modify(parent: AbstractTreeNode<*>, children: Collection<AbstractTreeNode<*>>, settings: ViewSettings?): Collection<AbstractTreeNode<*>> {
        if (parent !is PsiDirectoryNode) {
            return children
        }
        val basePath = project.basePath ?: return children
        val parentPath = parent.virtualFile?.path ?: return children
        if (parentPath != basePath) {
            return children
        }

        val pullRequestFile = BbCloudPullRequestVirtualFiles.forWorktreeProject(project) ?: return children
        return injectPullRequestNode(
            children = children,
            pullRequestFile = pullRequestFile,
            nodeFactory = {
                BbCloudPullRequestProjectViewNode(
                    project = project,
                    pullRequestFile = pullRequestFile,
                    settings = settings
                )
            }
        )
    }

    companion object {
        private val LOG = logger<BbCloudPullRequestTreeStructureProvider>()

        /**
         * Returns [children] with a node built from [nodeFactory] appended, unless a child already represents the same
         * pull request virtual file, in which case [children] is returned unchanged so repeated tree builds stay
         * idempotent (5a dedupe). [nodeFactory] is only invoked when a node is actually added.
         *
         * Keeps the placement and dedupe decision free of the node's construction so it can be unit-tested without a
         * live Project View (constructing a real [ProjectViewNode] requires platform application services).
         */
        fun injectPullRequestNode(
            children: Collection<AbstractTreeNode<*>>,
            pullRequestFile: BbCloudPullRequestVirtualFile,
            nodeFactory: () -> AbstractTreeNode<*>
        ): Collection<AbstractTreeNode<*>> {
            val alreadyPresent = children.any { child -> representsSamePullRequestFile(child, pullRequestFile) }
            if (alreadyPresent) {
                return children
            }
            LOG.debug("Added pull request node for #${pullRequestFile.pullRequestPath.pullRequestId} to the Project View")
            return children + nodeFactory()
        }

        private fun representsSamePullRequestFile(child: AbstractTreeNode<*>, pullRequestFile: BbCloudPullRequestVirtualFile): Boolean {
            val childFile = (child as? ProjectViewNode<*>)?.virtualFile
            return childFile is BbCloudPullRequestVirtualFile && childFile == pullRequestFile
        }
    }
}