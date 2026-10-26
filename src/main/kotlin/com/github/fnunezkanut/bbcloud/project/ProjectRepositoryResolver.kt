package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.bitbucket.BitbucketRepositoryRemoteUrl
import com.github.fnunezkanut.bbcloud.git.BitbucketGitRemoteReader
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The Bitbucket workspace and repository slug a local checkout belongs to, resolved as a pair. The workspace and slug
 * are always resolved together from the same source so they never disagree (a remote slug is never mixed with a
 * configured workspace or vice-versa).
 */
data class ResolvedProjectRepository(val workspace: String, val repoSlug: String)

/**
 * Resolves the Bitbucket workspace and repository slug of the checkout open at a project base path.
 *
 * For a checkout located directly under the configured `.worktrees` root, the repository slug is recovered from the
 * folder name in the form `<repoSlug>--pr-<pr>` and the caller-supplied configured workspace is used. This avoids
 * reading git metadata for worktrees, where the origin is stored in the shared repository configuration. For every
 * other checkout, the git **origin remote** is authoritative for both the workspace and the slug.
 *
 * When no usable Bitbucket remote can be read, the resolver falls back to the checkout folder name for the slug and the
 * caller-supplied configured workspace, preserving the historical behavior for non-git or non-Bitbucket checkouts.
 *
 * The resolver is pure and synchronous. Because [BitbucketGitRemoteReader] touches the filesystem, the reader is
 * injected behind [readRemoteUrl] so this stays unit-testable, and the caller is responsible for running it off the
 * event dispatch thread.
 */
object ProjectRepositoryResolver {
    fun resolve(
        projectBasePath: String?,
        configuredWorkspace: String?,
        readRemoteUrl: (Path) -> String? = BitbucketGitRemoteReader::readRemoteUrl,
        workTreeDirectory: String? = null
    ): ResolvedProjectRepository? {
        if (projectBasePath.isNullOrBlank()) {
            return null
        }

        val normalizedBasePath = Paths.get(projectBasePath).toAbsolutePath().normalize()
        val normalizedWorkTreeDirectory = workTreeDirectory
            ?.takeIf { path -> path.isNotBlank() }
            ?.let { path -> Paths.get(path).toAbsolutePath().normalize() }
        val folderName = normalizedBasePath.fileName?.toString()
        if (configuredWorkspace.isNullOrBlank().not() &&
            normalizedBasePath.parent == normalizedWorkTreeDirectory &&
            folderName != null
        ) {
            val worktreeRepoSlug = PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(folderName)
            if (worktreeRepoSlug != null) {
                return ResolvedProjectRepository(workspace = configuredWorkspace, repoSlug = worktreeRepoSlug)
            }
        }

        val remoteUrl = readRemoteUrl(Paths.get(projectBasePath))
        val remote = remoteUrl?.let { url -> BitbucketRepositoryRemoteUrl.parse(url) }
        if (remote != null) {
            // The git remote is authoritative for both the workspace and the slug.
            return ResolvedProjectRepository(workspace = remote.workspace, repoSlug = remote.repoSlug)
        }

        // No usable Bitbucket remote: fall back to the folder name slug and the configured workspace.
        val fallbackSlug = LocalProjectRepositorySlug.fromBasePath(projectBasePath)
        if (fallbackSlug == null || configuredWorkspace.isNullOrBlank()) {
            return null
        }
        return ResolvedProjectRepository(workspace = configuredWorkspace, repoSlug = fallbackSlug)
    }
}