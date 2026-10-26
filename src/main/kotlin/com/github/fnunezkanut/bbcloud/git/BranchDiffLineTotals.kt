package com.github.fnunezkanut.bbcloud.git

/**
 * Summed added and removed line counts for a pull request's local three-dot diff. [hasBinaryFiles] is true when
 * at least one file was binary (git numstat emits "-" instead of counts), so the totals exclude those files and
 * should be shown as approximate.
 */
data class BranchDiffLineTotals(val linesAdded: Int, val linesRemoved: Int, val hasBinaryFiles: Boolean)