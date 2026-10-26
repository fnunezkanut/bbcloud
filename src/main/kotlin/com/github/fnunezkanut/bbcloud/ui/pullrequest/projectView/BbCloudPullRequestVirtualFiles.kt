package com.github.fnunezkanut.bbcloud.ui.pullrequest.projectView

import com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService
import com.github.fnunezkanut.bbcloud.project.LocalProjectRepositorySlug
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeLayout
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestPath
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestVirtualFile
import com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestVirtualFileSystem
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.nio.file.Paths

/**
 * Builds the backing [BbCloudPullRequestVirtualFile] for the pull request a worktree project checks out, so the Project
 * View node and the editor opener can resolve the very same virtual file (by [BbCloudPullRequestPath] identity).
 *
 * Kept as a single source of truth for the path fields: the [BbCloudPullRequestPath.workspaceUrl] is the full
 * organization URL (for example `https://bitbucket.org/<slug>/`), matching what
 * [com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestEditorOpener.open] stores, so both callers
 * produce an equal file and the editor restoration flow reads the same workspace URL back out of the path.
 */
object BbCloudPullRequestVirtualFiles {
    /**
     * The pull request virtual file for the worktree checkout at [project]'s base path, or null when [project] is not a
     * pull request worktree or the metadata required to build a stable path is unavailable.
     *
     * Resolution is deliberately independent of whether the editor tab is open and of
     * [com.github.fnunezkanut.bbcloud.review.BbCloudWorktreePullRequestContext] binding: the worktree folder name is the
     * stable signal, mirroring [com.github.fnunezkanut.bbcloud.ui.pullrequest.editor.BbCloudPullRequestReviewBarRefresh].
     */
    fun forWorktreeProject(project: Project): BbCloudPullRequestVirtualFile? {
        val path = pathForWorktreeProject(
            projectBasePath = project.basePath,
            projectHash = project.locationHash,
            sessionId = BbCloudPullRequestVirtualFileSystem.sessionId(project),
            organizationUrl = BbCloudConfigurationService.getInstance().organizationUrlOrNull()
        ) ?: return null

        val fileSystem = BbCloudPullRequestVirtualFileSystem.getInstance()
        val file = fileSystem.refreshAndFindFileByPath(fileSystem.getPath(path)) as? BbCloudPullRequestVirtualFile
        if (file == null) {
            LOG.debug("No pull request node for PR #${path.pullRequestId}: the editor virtual file was not created")
            return null
        }
        return file
    }

    /**
     * Derives the [BbCloudPullRequestPath] identity of the pull request a worktree project checks out, or null when the
     * project is not a pull request worktree or the organization URL or repository slug cannot be resolved.
     *
     * Pure and free of platform singletons so the visibility rules can be unit-tested directly; [forWorktreeProject]
     * wires in the live [Project], VFS, and configuration service.
     */
    fun pathForWorktreeProject(projectBasePath: String?, projectHash: String, sessionId: String, organizationUrl: String?): BbCloudPullRequestPath? {
        if (projectBasePath.isNullOrBlank()) {
            LOG.debug("No pull request node: project has no base path")
            return null
        }

        val folderName = Paths.get(projectBasePath).fileName?.toString() ?: projectBasePath
        val pullRequestId = PullRequestWorktreeLayout.prNumberFromWorktreeFolderName(folderName)
        if (pullRequestId == null) {
            LOG.debug("No pull request node: $folderName is not a pull request worktree folder")
            return null
        }

        if (organizationUrl == null) {
            LOG.debug("No pull request node for PR #$pullRequestId: no organization URL is configured")
            return null
        }

        val repositorySlug = LocalProjectRepositorySlug.repoSlugForBasePath(projectBasePath)
        if (repositorySlug.isNullOrBlank()) {
            LOG.debug("No pull request node for PR #$pullRequestId: repository slug could not be resolved")
            return null
        }

        return BbCloudPullRequestPath(
            sessionId = sessionId,
            projectHash = projectHash,
            workspaceUrl = organizationUrl,
            repositorySlug = repositorySlug,
            pullRequestId = pullRequestId
        )
    }

    private val LOG = logger<BbCloudPullRequestVirtualFiles>()
}