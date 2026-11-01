package com.github.fnunezkanut.bbcloud.git

/**
 * Chooses the branch the current checkout should switch to when a pull request's branch collides with it, in the same
 * preference order the new-pull-request flow uses, then adapted for a collision that has no project base path to query:
 *   1. the Bitbucket `mainbranch` ([bitbucketMainBranch]), the authoritative answer when the API call succeeds;
 *   2. the pull request's [destinationBranch], the next best local fallback when the API call fails (the branch the PR
 *      merges into is almost always the repository's main line);
 *   3. the literal `"main"` as a last resort.
 *
 * Candidates equal to [prBranch] are skipped (trimmed, case-sensitive), because switching to the pull request branch
 * would not free it. `"master"` is deliberately not a blind fallback: the checkout would simply fail on repositories
 * without it, and the `"main"` fallback already carries that same risk and only produces a switch-failed balloon.
 *
 * Returns null when no candidate remains (for example a pull request whose source branch is the main branch).
 */
fun collisionMainBranch(bitbucketMainBranch: String?, destinationBranch: String?, prBranch: String): String? {
    val pr = prBranch.trim()
    val candidates = listOf(bitbucketMainBranch, destinationBranch, "main")
    for (candidate in candidates) {
        val trimmed = candidate?.trim()
        if (!trimmed.isNullOrBlank() && trimmed != pr) {
            return trimmed
        }
    }
    return null
}