package com.github.fnunezkanut.bbcloud.project

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The outcome of resolving the two directories a pull request worktree needs: the existing local clone of the
 * repository (the source the worktree is added to) and the target worktree path.
 */
sealed interface PullRequestWorktreeResolution {
    /**
     * Both directories resolved. [sourceCheckoutPath] is the existing local clone the worktree is added to and
     * [worktreePath] is the target `$workTreeDirectory/$repoSlug--pr-$prNumber` directory.
     */
    data class Resolved(val sourceCheckoutPath: Path, val worktreePath: Path) : PullRequestWorktreeResolution

    /** The work tree directory could not be derived (no projects directory configured) or is not a valid path. */
    data object WorkTreeDirMissing : PullRequestWorktreeResolution

    /** Neither the watched mapping folder nor the conventional `$projectsDirectory/$repoSlug` clone exists on disk. */
    data object SourceCheckoutMissing : PullRequestWorktreeResolution
}

/**
 * Resolves the source clone and the worktree target path for opening a pull request in its own git worktree.
 *
 * Two separate directories are involved and must not be confused:
 *  - the SOURCE clone: the existing local checkout of the repository, which the worktree is added to. It is resolved
 *    from the watched project mapping first (via [WatchedCheckoutResolver]), falling back to the conventional
 *    `$projectsDirectory/$repoSlug` location only when it exists on disk.
 *  - the WORKTREE directory: `$workTreeDirectory/$repoSlug--pr-$prNumber`, where the pull request is checked out.
 *
 * Unlike the shared-checkout flow, the projects directory IS consulted here as a fallback for the source clone, but
 * only when the watched mapping has no usable folder. It is never used as the worktree location.
 */
object PullRequestWorktreeResolver {
    fun resolve(
        mappings: List<WatchedProjectMapping>,
        workTreeDirectory: String?,
        projectsDirectory: String?,
        workspace: String,
        repoSlug: String,
        prNumber: Long,
        isDirectory: (Path) -> Boolean = { path -> Files.isDirectory(path) }
    ): PullRequestWorktreeResolution {
        // The work tree root is derived ($projectsDirectory/.worktrees) and created on demand by BbCloudWorktreeService,
        // so it is NOT required to already exist here; it only has to be a non-blank, valid path. A null/blank value
        // (no projects directory) or an invalid path is treated as WorkTreeDirMissing.
        val resolvedWorkTreeDirectory = normalizedPath(path = workTreeDirectory)
            ?: return PullRequestWorktreeResolution.WorkTreeDirMissing

        val sourceCheckoutPath = resolveSourceCheckout(
            mappings = mappings,
            projectsDirectory = projectsDirectory,
            workspace = workspace,
            repoSlug = repoSlug,
            isDirectory = isDirectory
        ) ?: return PullRequestWorktreeResolution.SourceCheckoutMissing

        val worktreePath = PullRequestWorktreeLayout.worktreePath(
            workTreeDirectory = resolvedWorkTreeDirectory.toString(),
            repoSlug = repoSlug,
            prNumber = prNumber
        )
        return PullRequestWorktreeResolution.Resolved(sourceCheckoutPath = sourceCheckoutPath, worktreePath = worktreePath)
    }

    /**
     * The existing source clone directory, or null when none exists. The watched mapping's folder wins when it exists;
     * otherwise the conventional `$projectsDirectory/$repoSlug` directory is used when it exists on disk.
     */
    private fun resolveSourceCheckout(
        mappings: List<WatchedProjectMapping>,
        projectsDirectory: String?,
        workspace: String,
        repoSlug: String,
        isDirectory: (Path) -> Boolean
    ): Path? {
        val watchedResolution = WatchedCheckoutResolver.resolve(
            mappings = mappings,
            workspace = workspace,
            repoSlug = repoSlug,
            isDirectory = isDirectory
        )
        if (watchedResolution is WatchedCheckoutResolution.Available) {
            return watchedResolution.checkoutPath
        }
        // The watched mapping has no usable folder (NoEntry or FolderMissing). Fall back to the conventional location.
        val projectsRoot = normalizedExistingDirectory(path = projectsDirectory, isDirectory = { true })
            ?: return null
        val conventionalClone = projectsRoot.resolve(repoSlug).toAbsolutePath().normalize()
        return if (isDirectory(conventionalClone)) conventionalClone else null
    }

    /** The trimmed path normalized to an absolute path, or null when it is null/blank or not a valid path. Unlike
     * [normalizedExistingDirectory] it does NOT require the path to exist on disk. */
    private fun normalizedPath(path: String?): Path? {
        val trimmed = path?.trim()
        if (trimmed.isNullOrEmpty()) {
            return null
        }
        return try {
            Paths.get(trimmed).toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            null
        }
    }

    private fun normalizedExistingDirectory(path: String?, isDirectory: (Path) -> Boolean): Path? {
        val trimmed = path?.trim()
        if (trimmed.isNullOrEmpty()) {
            return null
        }
        val resolved = try {
            Paths.get(trimmed).toAbsolutePath().normalize()
        } catch (_: InvalidPathException) {
            return null
        }
        return if (isDirectory(resolved)) resolved else null
    }
}