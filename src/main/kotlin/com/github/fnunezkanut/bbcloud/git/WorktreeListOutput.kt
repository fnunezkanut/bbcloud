package com.github.fnunezkanut.bbcloud.git

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Parses the output of `git worktree list --porcelain` to decide whether a path is already a registered worktree.
 *
 * The porcelain format lists each worktree as a block of attribute lines; the first line of every block is
 * `worktree <absolute-path>`. Comparing those paths (normalized to absolute, normalized form) against a candidate
 * path tells us whether `git worktree add` would collide with an existing registration, which drives the idempotent
 * reconciliation in [BbCloudWorktreeService]. Kept pure so the parsing is unit-testable with sample porcelain strings.
 */
object WorktreeListOutput {
    fun containsWorktree(porcelainOutput: String, worktreePath: Path): Boolean {
        val target = worktreePath.toAbsolutePath().normalize()
        return registeredWorktreePaths(porcelainOutput).any { registered -> registered == target }
    }

    private fun registeredWorktreePaths(porcelainOutput: String): List<Path> {
        return porcelainOutput.lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.startsWith(WORKTREE_PREFIX) }
            .mapNotNull { line -> parseWorktreePath(line) }
            .toList()
    }

    private fun parseWorktreePath(line: String): Path? {
        val rawPath = line.removePrefix(WORKTREE_PREFIX).trim()
        if (rawPath.isEmpty()) {
            return null
        }
        return try {
            Paths.get(rawPath).toAbsolutePath().normalize()
        } catch (_: Exception) {
            null
        }
    }

    private const val WORKTREE_PREFIX = "worktree "
}