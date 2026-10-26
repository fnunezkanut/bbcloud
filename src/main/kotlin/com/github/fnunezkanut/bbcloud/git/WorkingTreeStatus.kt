package com.github.fnunezkanut.bbcloud.git

/**
 * The checked-out branch and whether the working tree holds outstanding work.
 * [currentBranch] is null when HEAD is detached or the repository has no commits.
 */
data class WorkingTreeStatus(val currentBranch: String?, val hasUncommittedChanges: Boolean)