package com.github.fnunezkanut.bbcloud.project

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Decides whether a project window is a pull request worktree that bbcloud opened under the configured work tree root.
 */
object PullRequestWorktreeProject {
    /**
     * Returns true when [projectBasePath] is a git worktree checkout in `$workTreeDirectory/$repoSlug--pr-$prNumber`.
     *
     * A git worktree is recognized when `.git` at the project root is a regular file (a gitfile). The folder name must
     * parse as a pull request worktree and sit directly under [workTreeDirectory].
     */
    fun isPluginManagedWorktree(
        projectBasePath: String?,
        workTreeDirectory: String?,
        isGitWorktreeCheckout: (Path) -> Boolean = ::defaultIsGitWorktreeCheckout
    ): Boolean {
        if (projectBasePath.isNullOrBlank() || workTreeDirectory.isNullOrBlank()) {
            return false
        }
        val normalizedBasePath = runCatching { Paths.get(projectBasePath).toAbsolutePath().normalize() }.getOrNull()
            ?: return false
        val normalizedWorkTreeDirectory = runCatching {
            Paths.get(workTreeDirectory).toAbsolutePath().normalize()
        }.getOrNull() ?: return false
        if (normalizedBasePath.parent != normalizedWorkTreeDirectory) {
            return false
        }
        val folderName = normalizedBasePath.fileName?.toString() ?: return false
        if (PullRequestWorktreeLayout.prNumberFromWorktreeFolderName(folderName) == null) {
            return false
        }
        if (PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(folderName) == null) {
            return false
        }
        return isGitWorktreeCheckout(normalizedBasePath)
    }

    private fun defaultIsGitWorktreeCheckout(projectRoot: Path): Boolean {
        val dotGit = projectRoot.resolve(".git")
        return java.nio.file.Files.isRegularFile(dotGit)
    }
}