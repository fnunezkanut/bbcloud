package com.github.fnunezkanut.bbcloud.review

/**
 * The pure decision of whether the HEAD of a pull-request work tree belongs to the pull request.
 *
 * The decision is kept apart from the git and Bitbucket calls that produce its inputs, so it can be
 * unit-tested without a project, a repository or a network connection.
 */
internal object WorktreePullRequestHeadMembership {
    /**
     * Tells whether the HEAD of the work tree is part of the pull request.
     *
     * @param currentBranchName the branch the work tree is checked out on, or null for a detached HEAD.
     * @param headRevision the commit the work tree is checked out on.
     * @param sourceBranch the source branch of the pull request.
     * @param pullRequestCommitHashes the hashes of the commits Bitbucket reports for the pull request.
     * @param isCommitListTruncated whether Bitbucket stopped listing the commits at the page limit.
     * @param commitsKnown whether the commit list could be loaded at all.
     * @param headIsDescendantOfPullRequestTip whether the HEAD is a descendant of the tip of the pull request.
     */
    fun isHeadPartOfPullRequest(
        currentBranchName: String?,
        headRevision: String?,
        sourceBranch: String,
        pullRequestCommitHashes: List<String>,
        isCommitListTruncated: Boolean,
        commitsKnown: Boolean,
        headIsDescendantOfPullRequestTip: Boolean
    ): Boolean {
        if (headRevision.isNullOrBlank()) {
            return false
        }
        val branchMatches = isBranchMatching(currentBranchName = currentBranchName, sourceBranch = sourceBranch)
        val headRevisionInList = isRevisionInList(
            headRevision = headRevision,
            pullRequestCommitHashes = pullRequestCommitHashes
        )
        if (!commitsKnown) {
            return branchMatches
        }
        if (!branchMatches) {
            return headRevisionInList
        }
        return headRevisionInList || headIsDescendantOfPullRequestTip || isCommitListTruncated
    }

    /**
     * Tells whether the checked-out branch is the source branch of the pull request.
     *
     * A null or blank branch name means a detached HEAD, which never matches a branch.
     */
    fun isBranchMatching(currentBranchName: String?, sourceBranch: String): Boolean {
        val branch = currentBranchName?.takeIf { name -> name.isNotBlank() } ?: return false
        return stripRefsHeadsPrefix(branch) == stripRefsHeadsPrefix(sourceBranch)
    }

    /**
     * Tells whether the HEAD is one of the commits of the pull request.
     *
     * Bitbucket may report a short hash while the repository reports the full one, so a hash matches when one is
     * a prefix of the other. The comparison ignores the case of the hexadecimal digits.
     */
    fun isRevisionInList(headRevision: String?, pullRequestCommitHashes: List<String>): Boolean {
        val revision = headRevision?.takeIf { value -> value.isNotBlank() } ?: return false
        return pullRequestCommitHashes.any { hash -> hashesMatch(headRevision = revision, other = hash) }
    }

    /**
     * Tells whether two commit hashes denote the same commit.
     *
     * The shorter hash has to be at least [MINIMUM_HASH_LENGTH] characters long, so that a common short prefix does
     * not make two different commits look alike.
     */
    private fun hashesMatch(headRevision: String, other: String): Boolean {
        if (other.isBlank()) {
            return false
        }
        val left = headRevision.lowercase()
        val right = other.lowercase()
        if (left == right) {
            return true
        }
        if (left.length < MINIMUM_HASH_LENGTH || right.length < MINIMUM_HASH_LENGTH) {
            return false
        }
        return left.startsWith(right) || right.startsWith(left)
    }

    /** Strips the optional `refs/heads/` prefix of a branch name. */
    private fun stripRefsHeadsPrefix(branchName: String): String {
        return branchName.removePrefix(REFS_HEADS_PREFIX)
    }

    /** The prefix a fully qualified local branch name carries. */
    private const val REFS_HEADS_PREFIX = "refs/heads/"

    /** The shortest hash length that is safe to compare as a prefix. */
    private const val MINIMUM_HASH_LENGTH = 7
}