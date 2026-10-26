package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.git.WorkingTreeStatus

/**
 * The outcome of deciding whether to switch the local checkout of a watched project to a pull request's source branch.
 */
sealed interface WatchedBranchSwitchDecision {
    /** The checkout is already on the target branch; no switch is needed. */
    data object AlreadyOnBranch : WatchedBranchSwitchDecision

    /** The checkout has uncommitted changes and is on a different branch, so switching is unsafe. */
    data class SkipUncommittedChanges(val currentBranch: String?) : WatchedBranchSwitchDecision

    /** The Git state could not be read, so the branch must not be changed. */
    data object StateUnknown : WatchedBranchSwitchDecision

    /** The checkout can be switched to the target branch. */
    data object Switch : WatchedBranchSwitchDecision
}

/**
 * Decides what [WatchedBranchSwitchDecision] to take from the Git working-tree state and the pull request source branch.
 *
 * The rules run in that exact order, returning at the first match:
 *
 * 1. A `null` status (the Git probes failed) returns [WatchedBranchSwitchDecision.StateUnknown], so the caller never
 *    switches when the state is unknown.
 * 2. The current branch equaling the target returns [WatchedBranchSwitchDecision.AlreadyOnBranch]. This is checked
 *    before the dirty-tree rule because no switch is needed when the branch is already correct, even with a dirty
 *    working tree.
 * 3. A dirty working tree on a different branch returns
 *    [WatchedBranchSwitchDecision.SkipUncommittedChanges].
 * 4. Otherwise the working tree is clean and the branch differs, so [WatchedBranchSwitchDecision.Switch] is returned.
 *    A detached HEAD (`currentBranch == null`) with a clean working tree also reaches this branch.
 */
object WatchedBranchSwitchPolicy {
    fun decide(status: WorkingTreeStatus?, targetBranch: String): WatchedBranchSwitchDecision {
        if (status == null) {
            return WatchedBranchSwitchDecision.StateUnknown
        }
        if (status.currentBranch == targetBranch) {
            return WatchedBranchSwitchDecision.AlreadyOnBranch
        }
        if (status.hasUncommittedChanges) {
            return WatchedBranchSwitchDecision.SkipUncommittedChanges(currentBranch = status.currentBranch)
        }
        return WatchedBranchSwitchDecision.Switch
    }
}