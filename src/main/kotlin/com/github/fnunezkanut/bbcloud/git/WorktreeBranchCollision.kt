package com.github.fnunezkanut.bbcloud.git

/**
 * Pure decision for the one work-tree failure that is a user-resolvable precondition rather than a generic git error:
 * the source clone (the main checkout) is currently on the pull request's source branch, so git refuses to check that
 * branch out in a second worktree.
 *
 * The decision is structural (compare the source clone's current branch to the pull request branch) rather than a
 * string-match of git's "is already used by worktree" output, which is brittle across git versions and locales.
 */
object WorktreeBranchCollision {
    /**
     * Returns whether a failed `git worktree add`/`git checkout` is the branch-collision case.
     *
     * @param currentBranch the source clone's current branch (`null` when detached or on no branch).
     * @param prBranch the pull request source branch the worktree would be checked out on.
     * @param branchExists whether [prBranch] already exists as a local branch in the source clone; a freshly created
     *   branch (the `-b` path) cannot collide with the current checkout, so this must be true for a collision.
     */
    fun isCollision(currentBranch: String?, prBranch: String, branchExists: Boolean): Boolean {
        if (!branchExists) {
            return false
        }
        if (currentBranch == null) {
            return false
        }
        // Compare trimmed and case-sensitively: git refs are case-sensitive, and the caller already trims the pull
        // request branch, so a case difference is a different ref, not a collision.
        return currentBranch.trim() == prBranch.trim()
    }
}