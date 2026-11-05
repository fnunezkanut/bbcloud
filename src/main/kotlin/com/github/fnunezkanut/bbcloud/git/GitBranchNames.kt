package com.github.fnunezkanut.bbcloud.git

/** Normalizes Bitbucket branch names for use in git commands. */
object GitBranchNames {
    /**
     * Strips only a leading `<remote>/` and a leading `refs/heads/`. It never cuts at the last slash, because a branch
     * such as `feature/foo` is a real branch name.
     */
    fun normalize(branchName: String, remoteName: String): String {
        return branchName.trim()
            .removePrefix("$remoteName/")
            .removePrefix("refs/heads/")
    }
}