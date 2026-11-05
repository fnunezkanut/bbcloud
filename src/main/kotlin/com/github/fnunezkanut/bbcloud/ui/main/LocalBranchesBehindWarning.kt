package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.BbCloudBundle
import com.github.fnunezkanut.bbcloud.git.BranchSyncStatus
import com.github.fnunezkanut.bbcloud.git.PrBranchFastForwardPolicy
import com.github.fnunezkanut.bbcloud.git.PrBranchRole
import com.github.fnunezkanut.bbcloud.git.PrBranchSyncAction
import com.github.fnunezkanut.bbcloud.git.PrBranchSyncMode
import com.github.fnunezkanut.bbcloud.git.PrBranchSyncSkipReason
import com.github.fnunezkanut.bbcloud.git.PrBranchSyncSnapshot

/**
 * Builds the red warning of the `.pr` page from a [PrBranchSyncSnapshot]. Pure apart from the message bundle, so it is
 * unit-testable. Returns null when there is nothing to warn about.
 */
object LocalBranchesBehindWarning {
    fun from(snapshot: PrBranchSyncSnapshot?): HeaderLocalBehindRemote? {
        if (snapshot == null) {
            return null
        }
        val lines = PrBranchRole.entries.mapNotNull { role -> lineFor(snapshot = snapshot, role = role) }
        if (lines.isEmpty()) {
            return null
        }
        val roles = lines.map { line -> line.role }
        val linkKey = when {
            roles.size == PrBranchRole.entries.size -> "main.branch.localBehindRemote.updateBoth"
            roles.single() == PrBranchRole.Source -> "main.branch.localBehindRemote.updateSource"
            else -> "main.branch.localBehindRemote.updateDestination"
        }
        return HeaderLocalBehindRemote(
            title = BbCloudBundle.message("main.branch.localBehindRemote.title"),
            lines = lines,
            explanation = BbCloudBundle.message("main.branch.localBehindRemote.explanation"),
            updateLinkText = BbCloudBundle.message(linkKey)
        )
    }

    private fun lineFor(snapshot: PrBranchSyncSnapshot, role: PrBranchRole): LocalBranchBehindLine? {
        val status = snapshot.statusFor(role) as? BranchSyncStatus.Behind ?: return null
        val branch = snapshot.branchFor(role)
        val key = when (role) {
            PrBranchRole.Source -> "main.branch.localBehindRemote.source"
            PrBranchRole.Destination -> "main.branch.localBehindRemote.destination"
        }
        val base = BbCloudBundle.message(key, branch, status.commitsBehind, "${snapshot.remoteName}/$branch")
        val reason = reasonFor(snapshot = snapshot, role = role, status = status)
        return LocalBranchBehindLine(role = role, text = base + reason)
    }

    private fun reasonFor(snapshot: PrBranchSyncSnapshot, role: PrBranchRole, status: BranchSyncStatus.Behind): String {
        val action = PrBranchFastForwardPolicy.decide(
            role = role,
            status = status,
            prWorktreePath = snapshot.prWorktreePath,
            mode = PrBranchSyncMode.Automatic
        )
        val skip = action as? PrBranchSyncAction.Skip ?: return ""
        val path = status.checkedOutAt?.toString().orEmpty()
        return when (skip.reason) {
            PrBranchSyncSkipReason.UncommittedChanges -> {
                BbCloudBundle.message("main.branch.localBehindRemote.reason.uncommitted", path)
            }
            PrBranchSyncSkipReason.LocalCommitsNotOnRemote -> {
                BbCloudBundle.message("main.branch.localBehindRemote.reason.diverged", status.commitsAhead)
            }
            PrBranchSyncSkipReason.CheckedOutInOtherWorktree -> {
                BbCloudBundle.message("main.branch.localBehindRemote.reason.otherWorktree", path)
            }
            PrBranchSyncSkipReason.CheckedOutWorktreeStateUnknown -> {
                ""
            }
        }
    }
}