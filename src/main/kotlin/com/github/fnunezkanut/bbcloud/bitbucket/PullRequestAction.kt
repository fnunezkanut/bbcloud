package com.github.fnunezkanut.bbcloud.bitbucket

enum class PullRequestAction {
    Merge,
    Approve,
    Unapprove,
    MarkAsDraft,
    MarkAsReady,
    Decline
}

data class PullRequestDetails(
    val summary: PullRequestSummary,
    val isApprovedByCurrentUser: Boolean,
    val reviewers: List<PullRequestReviewer> = emptyList(),
    val authorUuid: String? = null,
    /** Raw Markdown description of the pull request; null when Bitbucket returned none. */
    val description: String? = null
)

sealed class PullRequestActionResult {
    data object Success : PullRequestActionResult()

    /** [message] is Bitbucket's structured error message, safe to show to the user. */
    data class Failed(val message: String? = null, val cause: Throwable? = null) : PullRequestActionResult()
}