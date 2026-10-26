package com.github.fnunezkanut.bbcloud.project

/** Selects worktrees whose PR number is absent from the complete open pull-request set. */
object StaleWorktreePruneSelection {
    /**
     * Returns discovered worktrees that are not represented by [openPrNumbers]. The open set must be COMPLETE:
     * callers must provide all pages fetched successfully without truncation or failure, never a partial listing.
     */
    fun worktreesToPrune(discovered: List<WatchedProjectWorktree>, openPrNumbers: Set<Long>): List<WatchedProjectWorktree> {
        return discovered.filter { worktree -> worktree.prNumber !in openPrNumbers }
    }
}