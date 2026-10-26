package com.github.fnunezkanut.bbcloud.bitbucket

/** The answer for one repository's reviewer query. */
sealed interface ReviewerQueueQueryResult {
    /** The open pull requests of the repository that the current user reviews and did not open. */
    data class Listed(val pullRequests: List<PullRequestSummary>) : ReviewerQueueQueryResult

    /** A 4xx answer other than 401: the repository is not in the workspace, or the token cannot read it. */
    data object Skipped : ReviewerQueueQueryResult

    /** A 401 answer: the API token is missing, invalid or expired. This fails the whole load. */
    data object Unauthorized : ReviewerQueueQueryResult

    /** A network error, a 5xx answer or an unreadable body. The repository contributes nothing and the load is marked truncated. */
    data class Failed(val cause: Throwable? = null) : ReviewerQueueQueryResult
}