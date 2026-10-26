package com.github.fnunezkanut.bbcloud.git

/**
 * The HEAD of a git worktree: the checked out branch (null when the HEAD is detached) and the current revision.
 */
data class WorktreeHead(val branchName: String?, val revision: String?)