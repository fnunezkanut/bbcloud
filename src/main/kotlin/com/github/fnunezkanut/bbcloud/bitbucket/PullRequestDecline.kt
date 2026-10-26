package com.github.fnunezkanut.bbcloud.bitbucket

/** The user's explicit choice in the decline-reason modal. */
sealed interface PullRequestDeclineDecision {
    data object Cancel : PullRequestDeclineDecision

    data class Confirmed(val reason: String) : PullRequestDeclineDecision

    companion object {
        fun confirmed(input: String): Confirmed {
            return Confirmed(reason = input.trim())
        }
    }
}

/** The prefix used for the optional top-level comment that records a decline reason. */
const val DECLINED_COMMENT_PREFIX = "Declined: "

fun declinedCommentBody(reason: String): String {
    return DECLINED_COMMENT_PREFIX + reason.trim()
}

sealed interface PullRequestDeclineResult {
    data class Success(val commentFailure: PullRequestActionResult.Failed? = null) : PullRequestDeclineResult

    data class Failed(val failure: PullRequestActionResult.Failed) : PullRequestDeclineResult
}