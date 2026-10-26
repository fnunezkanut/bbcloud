package com.github.fnunezkanut.bbcloud.bitbucket

/**
 * The pull requests of one repository split into the groups the Current Project tab lists separately:
 * the open ones, and everything Bitbucket already resolved (merged or declined).
 */
data class PullRequestListing(val open: List<PullRequestSummary>, val closed: List<PullRequestSummary>) {
    fun isEmpty(): Boolean {
        return open.isEmpty() && closed.isEmpty()
    }
}

object PullRequestOrdering {
    /**
     * Splits [pullRequests] into the open and the resolved ones, each newest-updated first, so the panel
     * can show one table per group. Closed means "not open": Bitbucket only reports merged and declined.
     */
    fun partitionForDisplay(pullRequests: List<PullRequestSummary>): PullRequestListing {
        val open = pullRequests.filter { summary -> summary.state == PullRequestState.Open }
        val closed = pullRequests.filter { summary -> summary.state != PullRequestState.Open }
        return PullRequestListing(
            open = newestFirst(open),
            closed = newestFirst(closed)
        )
    }

    private fun newestFirst(pullRequests: List<PullRequestSummary>): List<PullRequestSummary> {
        return pullRequests.sortedByDescending { summary -> summary.updatedOn }
    }
}