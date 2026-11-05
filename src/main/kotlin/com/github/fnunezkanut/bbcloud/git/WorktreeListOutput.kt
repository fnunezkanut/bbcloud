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

    /**
     * Returns the normalized path of the first worktree whose block carries `branch refs/heads/<branchName>`, or null
     * when the branch is not checked out in any worktree (detached and bare entries have no `branch` line).
     */
    fun worktreeForBranch(porcelainOutput: String, branchName: String): Path? {
        val expectedBranchLine = "$BRANCH_PREFIX$HEADS_PREFIX$branchName"
        val blocks = porcelainOutput.split(BLOCK_SEPARATOR)
        for (block in blocks) {
            val lines = block.lineSequence().map { line -> line.trim() }.toList()
            val worktreeLine = lines.firstOrNull { line -> line.startsWith(WORKTREE_PREFIX) } ?: continue
            if (lines.any { line -> line == expectedBranchLine }) {
                return parseWorktreePath(worktreeLine)
            }
        }
        return null
    }

    /** Returns the normalized absolute path of every worktree registered in [porcelainOutput]. */
    fun registeredPaths(porcelainOutput: String): List<Path> {
        return registeredWorktreePaths(porcelainOutput)
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
    private const val BRANCH_PREFIX = "branch "
    private const val HEADS_PREFIX = "refs/heads/"
    private val BLOCK_SEPARATOR = Regex("\\r?\\n\\s*\\r?\\n")
}