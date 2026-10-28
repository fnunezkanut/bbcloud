package com.github.fnunezkanut.bbcloud.bitbucket

enum class PullRequestAction {
    Merge,
    Approve,
    Unapprove,
    RequestChanges,
    RemoveChangesRequest,
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
    val description: String? = null,
    /** True when the current user's participant entry is in the `changes_requested` state. */
    val isChangesRequestedByCurrentUser: Boolean = false,
    /** True when the current user authored the pull request; Bitbucket rejects review actions on one's own pull request. */
    val isAuthoredByCurrentUser: Boolean = false
)

sealed class PullRequestActionResult {
    data object Success : PullRequestActionResult()

    /** [message] is Bitbucket's structured error message, safe to show to the user. */
    data class Failed(val message: String? = null, val cause: Throwable? = null) : PullRequestActionResult()
}