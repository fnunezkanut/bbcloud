package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeLayout.prNumberFromWorktreeFolderName
import com.github.fnunezkanut.bbcloud.project.PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName
import java.nio.file.Files
import java.nio.file.Path

/** A single discovered pull request worktree for a watched repository. */
data class WatchedProjectWorktree(val repoSlug: String, val prNumber: Long, val worktreePath: Path)

/** The UI view model of one worktree entry shown inside a watched-projects table row. */
data class WatchedProjectWorktreeRow(val prNumber: Long, val branchName: String?, val worktreePath: Path)

/**
 * Discovers pull request worktrees for a watched repository directly from the filesystem.
 *
 * Worktrees live flattened under the work tree root as `$repoSlug--pr-$prNumber` folders (see
 * [PullRequestWorktreeLayout]). Discovery is filesystem-only: no Bitbucket API call happens here, so it is cheap and
 * safe to run whenever the watched-projects table is rendered or refreshed.
 */
object PullRequestWorktreeListing {
    /**
     * Lists the PR worktrees for [repoSlug] under [workTreeDirectory].
     *
     * Immediate child directories of [workTreeDirectory] are listed via the injected [listChildren] (so the function
     * is unit-testable without the filesystem). Each folder name is parsed with
     * [PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName] and [prNumberFromWorktreeFolderName]; only entries
     * whose parsed slug equals [repoSlug] are kept, sorted by [prNumber] ascending.
     *
     * Returns an empty list (never throws) when [workTreeDirectory] does not exist or is unreadable.
     */
    fun worktreesForRepo(workTreeDirectory: Path, repoSlug: String, listChildren: (Path) -> List<Path> = ::listChildDirectories): List<WatchedProjectWorktree> {
        val children = runCatching { listChildren(workTreeDirectory) }.getOrDefault(emptyList())
        return children
            .mapNotNull { child ->
                val folderName = child.fileName.toString()
                val slug = repoSlugFromWorktreeFolderName(folderName)
                val prNumber = prNumberFromWorktreeFolderName(folderName)
                if (slug == repoSlug && prNumber != null) {
                    WatchedProjectWorktree(repoSlug = slug, prNumber = prNumber, worktreePath = child)
                } else {
                    null
                }
            }
            .sortedBy { it.prNumber }
    }

    /** Reads immediate child directories of [directory] from the filesystem, or an empty list when unreadable. */
    fun listChildDirectories(directory: Path): List<Path> {
        return runCatching {
            Files.list(directory)
                .use { stream ->
                    stream
                        .filter { Files.isDirectory(it) }
                        .toList()
                }
        }.getOrDefault(emptyList())
    }
}