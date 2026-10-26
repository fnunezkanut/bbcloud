package com.github.fnunezkanut.bbcloud.git

import com.intellij.openapi.vcs.changes.Change

sealed interface BranchDiffResult {
    data class Success(
        val changes: Collection<Change>,
        val destinationRef: String,
        val sourceRef: String,
        val fileLineCounts: Map<String, FileLineCount> = emptyMap(),
        val fileRevisions: Map<String, String> = emptyMap()
    ) : BranchDiffResult

    data object NoRepository : BranchDiffResult

    data class BranchNotFound(val branchName: String) : BranchDiffResult

    data class Failed(val message: String, val cause: Throwable? = null) : BranchDiffResult
}