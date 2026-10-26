package com.github.fnunezkanut.bbcloud.git

import java.nio.file.Path

/**
 * The outcome of preparing a git worktree for a pull request.
 */
sealed class WorktreeSetupResult {
    /** The worktree at [worktreePath] exists and is checked out on the pull request branch. */
    data class Success(val worktreePath: Path) : WorktreeSetupResult()

    /**
     * The worktree could not be prepared. [output] is the trimmed combined output of the failing git command (or a
     * short description for a non-git failure such as a folder-creation error), suitable for logging.
     */
    data class Failed(val output: String, val cause: Throwable? = null) : WorktreeSetupResult()

    /**
     * The work tree could not be created because the source clone (the main checkout) is currently on the pull
     * request's source branch, so git refuses to check that branch out in a second worktree. This is a user-resolvable
     * precondition (stash or commit in-progress work, switch the current checkout to another branch, then retry), not a
     * generic git error, so the UI shows an explanatory modal rather than the raw git-output balloon.
     */
    data class BranchCheckedOutInSource(val branchName: String, val sourceCheckoutPath: Path) : WorktreeSetupResult()
}