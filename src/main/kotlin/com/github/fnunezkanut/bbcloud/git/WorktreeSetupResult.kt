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
     * precondition, not a generic git error.
     *
     * The UI first tries to resolve it automatically: when the colliding clone is this window's own checkout and the
     * working tree is clean, it switches the checkout to the repository main branch and retries once; when the working
     * tree is dirty it warns the user instead. The explanatory modal is only the fallback for collisions that cannot be
     * resolved automatically (another window's clone, no usable main branch, or an unreadable status).
     */
    data class BranchCheckedOutInSource(val branchName: String, val sourceCheckoutPath: Path) : WorktreeSetupResult()
}