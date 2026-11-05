package com.github.fnunezkanut.bbcloud.git

import java.nio.file.Path

/** The sync state of both pull-request branches at one point in time. */
data class PrBranchSyncSnapshot(
    val remoteName: String,
    val sourceBranch: String,
    val destinationBranch: String,
    val prWorktreePath: Path,
    val source: BranchSyncStatus,
    val destination: BranchSyncStatus
) {
    fun isAnyBehind(): Boolean {
        return source is BranchSyncStatus.Behind || destination is BranchSyncStatus.Behind
    }

    fun statusFor(role: PrBranchRole): BranchSyncStatus {
        return when (role) {
            PrBranchRole.Source -> source
            PrBranchRole.Destination -> destination
        }
    }

    fun branchFor(role: PrBranchRole): String {
        return when (role) {
            PrBranchRole.Source -> sourceBranch
            PrBranchRole.Destination -> destinationBranch
        }
    }
}