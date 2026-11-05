package com.github.fnunezkanut.bbcloud.git

import java.nio.file.Path

/** What happened to one branch during a fast-forward pass. */
sealed interface PrBranchSyncStepResult {
    data object NotNeeded : PrBranchSyncStepResult

    /** The branch was moved; [path] is the work tree that was changed, or null when only a ref was written. */
    data class Updated(val path: Path?) : PrBranchSyncStepResult

    data class Skipped(val reason: PrBranchSyncSkipReason, val path: Path? = null) : PrBranchSyncStepResult

    data class Failed(val output: String) : PrBranchSyncStepResult
}

/** The per-branch results of a fast-forward pass. */
data class PrBranchSyncOutcome(val results: Map<PrBranchRole, PrBranchSyncStepResult>) {
    val anyRefMoved: Boolean
        get() {
            return results.values.any { result -> result is PrBranchSyncStepResult.Updated }
        }

    fun resultFor(role: PrBranchRole): PrBranchSyncStepResult {
        return results[role] ?: PrBranchSyncStepResult.NotNeeded
    }
}

/** The outcome of a full sync plus the state of both branches afterwards. */
data class PrBranchSyncResult(val outcome: PrBranchSyncOutcome, val after: PrBranchSyncSnapshot)