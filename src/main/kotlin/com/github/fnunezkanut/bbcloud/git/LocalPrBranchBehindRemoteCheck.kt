package com.github.fnunezkanut.bbcloud.git

import com.github.fnunezkanut.bbcloud.review.WorktreePullRequestHeadMembership

/**
 * The pure decision of whether the local pull-request branch is behind its remote counterpart.
 *
 * The decision is kept apart from the git calls that produce its inputs, so it can be unit-tested without a project,
 * a repository, or a fetch. It mirrors the gate the orange "commits behind destination" line uses: the banner only
 * shows when the work tree is checked out on the pull request source branch (decision 3b) and `origin/<sourceBranch>`
 * carries at least one commit the local HEAD does not have (decision 4a).
 */
object LocalPrBranchBehindRemoteCheck {
    /**
     * Decides whether to show the local-behind-remote banner and, when it does, how many commits the local branch is
     * behind the remote.
     *
     * @param currentBranchName the branch the work tree is checked out on, or null for a detached HEAD.
     * @param sourceBranch the source branch of the pull request.
     * @param localRevision the revision the work tree HEAD points at, or null when it could not be read.
     * @param commitsBehindRemote `git rev-list --count <local>..origin/<sourceBranch>`, or null when the count could
     *   not be computed (a fetch failure, a missing remote ref, or a failing command).
     */
    fun evaluate(currentBranchName: String?, sourceBranch: String, localRevision: String?, commitsBehindRemote: Int?): LocalBehindRemoteDecision {
        if (!WorktreePullRequestHeadMembership.isBranchMatching(
                currentBranchName = currentBranchName,
                sourceBranch = sourceBranch
            )
        ) {
            return LocalBehindRemoteDecision.Hidden
        }
        if (localRevision.isNullOrBlank()) {
            return LocalBehindRemoteDecision.Hidden
        }
        if (commitsBehindRemote == null || commitsBehindRemote <= 0) {
            return LocalBehindRemoteDecision.Hidden
        }
        return LocalBehindRemoteDecision.Behind(commitsBehind = commitsBehindRemote)
    }
}

/** The outcome of [LocalPrBranchBehindRemoteCheck]: either hidden, or behind by a known positive commit count. */
sealed interface LocalBehindRemoteDecision {
    data object Hidden : LocalBehindRemoteDecision

    data class Behind(val commitsBehind: Int) : LocalBehindRemoteDecision
}