package com.github.fnunezkanut.bbcloud.bitbucket

sealed class AuthoredPullRequestListResult {
    /**
     * [isTruncated] means the listing is knowingly incomplete: Bitbucket offered more pages than were read, or a
     * row named no repository and had to be dropped. The panel says so rather than letting a short table pass for
     * every pull request the user opened.
     */
    data class Success(val pullRequests: List<ReviewingPullRequest>, val isTruncated: Boolean = false) : AuthoredPullRequestListResult()

    data class Failed(val cause: Throwable? = null) : AuthoredPullRequestListResult()
}