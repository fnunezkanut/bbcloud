package com.github.fnunezkanut.bbcloud.ui.pullrequest.create

import com.github.fnunezkanut.bbcloud.git.WorkingTreeStatus

/**
 * Pure rule mapping a working-tree status to a [WorkingTreeCheck]. A create would switch the current checkout to the
 * main branch, so uncommitted changes on a non-main branch must block the create to avoid carrying or clobbering local
 * work.
 */
object NewPullRequestWorkingTreeRule {
    fun evaluate(status: WorkingTreeStatus?, mainBranchNames: Set<String>): WorkingTreeCheck {
        if (status == null) {
            // No repository, or a probe failed; do not block, the caller logs this at info.
            return WorkingTreeCheck.Unknown
        }
        if (!status.hasUncommittedChanges) {
            return WorkingTreeCheck.Clean
        }
        val currentBranch = status.currentBranch?.trim()
        // Uncommitted changes on the main branch are allowed, because the post-create switch is then a no-op. Git refs
        // are case-sensitive, so the branch name is matched case-sensitively.
        if (currentBranch != null && currentBranch in mainBranchNames) {
            return WorkingTreeCheck.Clean
        }
        return WorkingTreeCheck.BlockedUncommittedChanges(currentBranch = status.currentBranch)
    }
}