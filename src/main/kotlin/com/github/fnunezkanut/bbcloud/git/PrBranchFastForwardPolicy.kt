package com.github.fnunezkanut.bbcloud.git

import java.nio.file.Path

/** Who asked for the sync: the automatic triggers are conservative, a user click may touch more. */
enum class PrBranchSyncMode {
    Automatic,
    UserRequested
}

/** Why a behind branch was left alone. */
enum class PrBranchSyncSkipReason {
    UncommittedChanges,
    LocalCommitsNotOnRemote,
    CheckedOutInOtherWorktree,
    CheckedOutWorktreeStateUnknown
}

/** What to do about one branch. */
sealed interface PrBranchSyncAction {
    data object None : PrBranchSyncAction

    data class MergeFastForwardInWorktree(val worktreePath: Path) : PrBranchSyncAction

    data class UpdateRef(val newRevision: String, val oldRevision: String) : PrBranchSyncAction

    data object CreateLocalBranch : PrBranchSyncAction

    data object PullIntoPrWorktree : PrBranchSyncAction

    data class Skip(val reason: PrBranchSyncSkipReason) : PrBranchSyncAction
}

/** The pure rules that decide how a pull-request branch may be brought in line with its remote. */
object PrBranchFastForwardPolicy {
    fun decide(role: PrBranchRole, status: BranchSyncStatus, prWorktreePath: Path, mode: PrBranchSyncMode): PrBranchSyncAction {
        if (status is BranchSyncStatus.MissingLocal) {
            return PrBranchSyncAction.CreateLocalBranch
        }
        if (status !is BranchSyncStatus.Behind) {
            return PrBranchSyncAction.None
        }
        val checkedOutAt = status.checkedOutAt?.toAbsolutePath()?.normalize()
        val prWorktree = prWorktreePath.toAbsolutePath().normalize()
        if (status.isDiverged) {
            return decideDiverged(role = role, checkedOutAt = checkedOutAt, prWorktree = prWorktree, mode = mode, status = status)
        }
        if (checkedOutAt == null) {
            return PrBranchSyncAction.UpdateRef(newRevision = status.remoteRevision, oldRevision = status.localRevision)
        }
        val dirty = status.checkedOutWorktreeDirty
            ?: return PrBranchSyncAction.Skip(PrBranchSyncSkipReason.CheckedOutWorktreeStateUnknown)
        if (checkedOutAt == prWorktree) {
            return if (dirty) {
                PrBranchSyncAction.Skip(PrBranchSyncSkipReason.UncommittedChanges)
            } else {
                PrBranchSyncAction.MergeFastForwardInWorktree(worktreePath = checkedOutAt)
            }
        }
        return when {
            mode == PrBranchSyncMode.Automatic -> PrBranchSyncAction.Skip(PrBranchSyncSkipReason.CheckedOutInOtherWorktree)
            dirty -> PrBranchSyncAction.Skip(PrBranchSyncSkipReason.UncommittedChanges)
            else -> PrBranchSyncAction.MergeFastForwardInWorktree(worktreePath = checkedOutAt)
        }
    }

    private fun decideDiverged(
        role: PrBranchRole,
        checkedOutAt: Path?,
        prWorktree: Path,
        mode: PrBranchSyncMode,
        status: BranchSyncStatus.Behind
    ): PrBranchSyncAction {
        val canPull = mode == PrBranchSyncMode.UserRequested &&
            role == PrBranchRole.Source &&
            checkedOutAt == prWorktree &&
            status.checkedOutWorktreeDirty == false
        if (status.checkedOutAt != null && status.checkedOutWorktreeDirty == null) {
            return PrBranchSyncAction.Skip(PrBranchSyncSkipReason.CheckedOutWorktreeStateUnknown)
        }
        return if (canPull) {
            PrBranchSyncAction.PullIntoPrWorktree
        } else {
            PrBranchSyncAction.Skip(PrBranchSyncSkipReason.LocalCommitsNotOnRemote)
        }
    }
}