package com.github.fnunezkanut.bbcloud.project

import java.nio.file.Paths

object LocalProjectRepositorySlug {
    fun fromBasePath(basePath: String?): String? {
        if (basePath.isNullOrBlank()) {
            return null
        }
        return Paths.get(basePath).fileName?.toString()?.takeIf { it.isNotEmpty() }
    }

    /**
     * The Bitbucket repository slug for the checkout at [basePath], with the pull request worktree suffix removed.
     *
     * Pull request worktrees are named `<repoSlug>--pr-<prNumber>` (see [PullRequestWorktreeLayout]); the raw folder
     * name would otherwise leak the `--pr-<n>` suffix into Bitbucket URLs. When the folder is not a worktree folder,
     * the folder name is used unchanged, which is the previous behavior.
     */
    fun repoSlugForBasePath(basePath: String?): String? {
        val folderName = fromBasePath(basePath) ?: return null
        return PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(folderName) ?: folderName
    }

    /**
     * Resolves the Bitbucket repository slug of the project at [basePath] using the explicit [explicitSlug] when it is
     * present, falling back to the checkout folder name otherwise. The explicit slug is the Bitbucket repository slug
     * parsed from the origin remote URL or supplied by the caller; the folder name is only used when no explicit slug is
     * available, because the local checkout folder can differ from the Bitbucket slug.
     */
    fun resolve(explicitSlug: String?, basePath: String?): String? {
        if (explicitSlug != null && explicitSlug.isNotBlank()) {
            return explicitSlug.trim()
        }
        return fromBasePath(basePath)
    }
}