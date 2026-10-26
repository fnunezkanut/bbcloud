package com.github.fnunezkanut.bbcloud.project

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Computes the on-disk layout of a pull request git worktree.
 *
 * A pull request opened from the Workspace tabs is checked out into its own git worktree so that the shared local
 * clone is never mutated. The worktree lives at `$workTreeDirectory/$repoSlug--pr-$prNumber`, a single flattened
 * folder per pull request directly under the work tree root (no intermediate per-repository grouping folder).
 */
object PullRequestWorktreeLayout {
    /** The separator joining the repository slug and the pull request number in the flattened worktree folder name. */
    private const val SLUG_PR_SEPARATOR = "--pr-"

    /**
     * The absolute worktree directory for a pull request, `$workTreeDirectory/$repoSlug--pr-$prNumber`.
     *
     * The slug and pull request number are joined with `--pr-` into a single folder segment. The composed name is also
     * parsed by [ProjectRepositoryResolver] through [repoSlugFromWorktreeFolderName] when recovering a repository
     * slug from a checkout directly under the configured work tree root. The format is unambiguous for real inputs
     * (Bitbucket slugs are lower-cased alphanumeric-and-hyphen and never contain a double dash, and the PR number is a
     * positive `Long`).
     *
     * @param workTreeDirectory the configured work tree root (see
     *   [com.github.fnunezkanut.bbcloud.config.BbCloudConfigurationService.workTreeDirectoryOrNull]).
     * @param repoSlug the Bitbucket repository slug (the workspace is already stripped).
     * @param prNumber the pull request number, which is
     *   [com.github.fnunezkanut.bbcloud.bitbucket.PullRequestSummary.id].
     */
    fun worktreePath(workTreeDirectory: String, repoSlug: String, prNumber: Long): Path {
        return Paths.get(workTreeDirectory)
            .resolve("$repoSlug$SLUG_PR_SEPARATOR$prNumber")
            .toAbsolutePath()
            .normalize()
    }

    /**
     * Recovers the repository slug from a flattened worktree folder name, or returns null when the name is malformed.
     *
     * The pull request suffix must contain ASCII digits that parse as a positive [Long].
     */
    fun repoSlugFromWorktreeFolderName(folderName: String): String? {
        val separatorIndex = folderName.lastIndexOf(SLUG_PR_SEPARATOR)
        if (separatorIndex <= 0) {
            return null
        }

        val repoSlug = folderName.substring(0, separatorIndex)
        val pullRequestNumber = folderName.substring(separatorIndex + SLUG_PR_SEPARATOR.length)
        if (pullRequestNumber.isEmpty() || pullRequestNumber.any { character -> character !in '0'..'9' }) {
            return null
        }

        val parsedNumber = pullRequestNumber.toLongOrNull()
        if (parsedNumber == null || parsedNumber <= 0L) {
            return null
        }
        return repoSlug
    }

    /**
     * Recovers the pull request number from a flattened worktree folder name, or returns null when the name is
     * malformed or does not encode a positive pull request number.
     *
     * Mirrors [repoSlugFromWorktreeFolderName]: finds the last [SLUG_PR_SEPARATOR] (`"--pr-"`) and validates the
     * suffix is non-empty ASCII digits parsing as a positive [Long].
     */
    fun prNumberFromWorktreeFolderName(folderName: String): Long? {
        val separatorIndex = folderName.lastIndexOf(SLUG_PR_SEPARATOR)
        if (separatorIndex <= 0) {
            return null
        }

        val pullRequestNumber = folderName.substring(separatorIndex + SLUG_PR_SEPARATOR.length)
        if (pullRequestNumber.isEmpty() || pullRequestNumber.any { character -> character !in '0'..'9' }) {
            return null
        }

        return pullRequestNumber.toLongOrNull()?.takeIf { it > 0L }
    }

    /**
     * The parent of a worktree, which under the flattened layout is the work tree root itself (`.worktrees`). Callers
     * create this folder before adding the worktree, since `git worktree add` does not create missing parent
     * directories.
     */
    fun parentDirectory(worktreePath: Path): Path {
        return worktreePath.parent
    }
}