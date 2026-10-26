package com.github.fnunezkanut.bbcloud.project

import java.nio.file.Path
import java.nio.file.Paths

/** Pure identity check for the project that owns a declined pull-request worktree. */
object PullRequestWorktreeMatch {
    fun expectedWorktreePath(
        projectBasePath: String?,
        workTreeDirectory: String?,
        currentRepositorySlug: String,
        currentPullRequestId: Long,
        declinedRepositorySlug: String,
        declinedPullRequestId: Long
    ): Path? {
        if (currentRepositorySlug != declinedRepositorySlug || currentPullRequestId != declinedPullRequestId) {
            return null
        }
        val normalizedWorkTreeDirectory = normalizedPath(workTreeDirectory) ?: return null
        val expectedPath = PullRequestWorktreeLayout.worktreePath(
            workTreeDirectory = normalizedWorkTreeDirectory.toString(),
            repoSlug = declinedRepositorySlug,
            prNumber = declinedPullRequestId
        )
        val normalizedProjectPath = normalizedPath(projectBasePath) ?: return null
        return expectedPath.takeIf { path -> normalizedProjectPath == path }
    }

    private fun normalizedPath(path: String?): Path? {
        val trimmed = path?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return runCatching { Paths.get(trimmed).toAbsolutePath().normalize() }.getOrNull()
    }
}