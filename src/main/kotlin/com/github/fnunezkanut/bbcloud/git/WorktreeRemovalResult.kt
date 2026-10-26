package com.github.fnunezkanut.bbcloud.git

/** Outcome of removing one validated pull-request worktree and pruning stale registrations. */
sealed interface WorktreeRemovalResult {
    data object Success : WorktreeRemovalResult

    data class Failed(val output: String, val cause: Throwable? = null) : WorktreeRemovalResult
}