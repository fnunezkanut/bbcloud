package com.github.fnunezkanut.bbcloud.ui.pullrequest.editor

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem

class BbCloudPullRequestVirtualFileSystem : ComplexPathVirtualFileSystem<BbCloudPullRequestPath>(BbCloudPullRequestPathSerializer) {
    override fun getProtocol(): String {
        return PROTOCOL
    }

    public override fun findOrCreateFile(project: Project, path: BbCloudPullRequestPath): VirtualFile? {
        if (project.locationHash != path.projectHash) {
            LOG.debug("Rejected pull request editor path for project ${project.name}: project hash did not match")
            return null
        }
        return BbCloudPullRequestVirtualFile(
            project = project,
            pullRequestPath = path,
            fileSystem = this
        )
    }

    companion object {
        const val PROTOCOL = "bbcloudpr"

        fun getInstance(): BbCloudPullRequestVirtualFileSystem {
            return ProjectManager.getInstance().openProjects.firstOrNull()?.let { project ->
                com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                    .getFileSystem(PROTOCOL) as? BbCloudPullRequestVirtualFileSystem
            } ?: BbCloudPullRequestVirtualFileSystem()
        }

        fun sessionId(project: Project): String {
            return project.locationHash
        }

        fun getPath(sessionId: String, project: Project, workspaceUrl: String, repositorySlug: String, pullRequestId: Long): String {
            val path = BbCloudPullRequestPath(
                sessionId = sessionId,
                projectHash = project.locationHash,
                workspaceUrl = BbCloudPullRequestPathSerializer.normalizeWorkspaceUrl(workspaceUrl),
                repositorySlug = repositorySlug,
                pullRequestId = pullRequestId
            )
            return getInstance().getPath(path)
        }

        private val LOG = logger<BbCloudPullRequestVirtualFileSystem>()
    }
}