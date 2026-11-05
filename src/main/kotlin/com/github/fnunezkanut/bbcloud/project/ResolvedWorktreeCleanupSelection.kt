package com.github.fnunezkanut.bbcloud.project

import java.nio.file.Path

/** A PR work tree that should be removed. [directoryExists] is true when the folder was found on disk. */
data class ResolvedWorktreeCandidate(val prNumber: Long, val worktreePath: Path, val directoryExists: Boolean)

/**
 * Selects PR work trees to clean up in the background: those of merged or declined pull requests, whether they exist
 * as a directory, as a git registration whose directory is gone, or both.
 */
object ResolvedWorktreeCleanupSelection {
    /**
     * @param completeOpenPrNumbers every open pull request number, or null when that listing failed. When null only
     * [resolvedPrNumbers] drives the selection. A PR in the open set is never selected.
     */
    fun candidates(
        repoSlug: String,
        workTreeDirectory: Path,
        discoveredDirectories: List<WatchedProjectWorktree>,
        registeredPaths: List<Path>,
        resolvedPrNumbers: Set<Long>,
        completeOpenPrNumbers: Set<Long>?
    ): List<ResolvedWorktreeCandidate> {
        val normalizedRoot = workTreeDirectory.toAbsolutePath().normalize()
        val byPath = linkedMapOf<Path, ResolvedWorktreeCandidate>()
        for (registered in registeredPaths) {
            val path = registered.toAbsolutePath().normalize()
            val prNumber = prNumberOfRegistration(path = path, root = normalizedRoot, repoSlug = repoSlug) ?: continue
            byPath[path] = ResolvedWorktreeCandidate(prNumber = prNumber, worktreePath = path, directoryExists = false)
        }
        for (discovered in discoveredDirectories) {
            val path = discovered.worktreePath.toAbsolutePath().normalize()
            byPath[path] = ResolvedWorktreeCandidate(prNumber = discovered.prNumber, worktreePath = path, directoryExists = true)
        }
        return byPath.values
            .filter { candidate -> isResolved(candidate.prNumber, resolvedPrNumbers, completeOpenPrNumbers) }
            .sortedBy { candidate -> candidate.prNumber }
    }

    private fun isResolved(prNumber: Long, resolvedPrNumbers: Set<Long>, completeOpenPrNumbers: Set<Long>?): Boolean {
        if (completeOpenPrNumbers != null && prNumber in completeOpenPrNumbers) {
            return false
        }
        return prNumber in resolvedPrNumbers || completeOpenPrNumbers != null
    }

    private fun prNumberOfRegistration(path: Path, root: Path, repoSlug: String): Long? {
        if (path.parent != root) {
            return null
        }
        val folderName = path.fileName?.toString() ?: return null
        if (PullRequestWorktreeLayout.repoSlugFromWorktreeFolderName(folderName) != repoSlug) {
            return null
        }
        return PullRequestWorktreeLayout.prNumberFromWorktreeFolderName(folderName)
    }
}