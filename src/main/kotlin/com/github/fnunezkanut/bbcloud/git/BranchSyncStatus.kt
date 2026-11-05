package com.github.fnunezkanut.bbcloud.git

import java.nio.file.Path

/** The state of one local pull-request branch relative to its `<remote>/<branch>` counterpart. */
sealed interface BranchSyncStatus {
    /**
     * There is no `refs/heads/<b>` but the remote tip exists (common for the destination in a fresh clone). Never
     * warns; the fast-forward policy turns it into a local branch creation.
     */
    data class MissingLocal(val remoteRevision: String) : BranchSyncStatus

    /** The remote tracking ref does not exist after the fetch (deleted branch, fork PR). Never warns. */
    data object NoRemoteBranch : BranchSyncStatus

    /** A git command failed, so nothing is known. Never warns. */
    data object Unknown : BranchSyncStatus

    /** The local branch is not behind. Local commits that are not pushed are not "behind". */
    data class UpToDate(val commitsAhead: Int) : BranchSyncStatus

    /** The local branch lacks [commitsBehind] commits that the remote has. */
    data class Behind(
        val commitsBehind: Int,
        val commitsAhead: Int,
        val localRevision: String,
        val remoteRevision: String,
        val checkedOutAt: Path?,
        val checkedOutWorktreeDirty: Boolean?
    ) : BranchSyncStatus {
        val isDiverged: Boolean
            get() {
                return commitsAhead > 0
            }
    }
}