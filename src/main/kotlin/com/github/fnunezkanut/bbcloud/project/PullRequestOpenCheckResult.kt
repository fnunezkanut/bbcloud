package com.github.fnunezkanut.bbcloud.project

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestDetails
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestState

/**
 * The outcome of re-checking with Bitbucket whether a pull request is still open at the moment it is clicked.
 *
 * The flow distinguishes "the pull request is no longer open" ([NotOpen]) from "the pull request could not be found"
 * ([NotFound]) and from "the lookup itself failed" ([Failed]) so each path logs a distinct reason before redirecting
 * the user to the pull request on Bitbucket.
 */
sealed interface PullRequestOpenCheckResult {
    /** The pull request still exists and is still open, so the worktree flow may continue. */
    data class StillOpen(val details: PullRequestDetails) : PullRequestOpenCheckResult

    /** The pull request exists but is no longer open; [state] is its current, non-open state. */
    data class NotOpen(val state: PullRequestState) : PullRequestOpenCheckResult

    /** Bitbucket returned no details for the pull request, so it is treated as not found. */
    data object NotFound : PullRequestOpenCheckResult

    /** The state check could not be performed; [cause] is the failure when one is available. */
    data class Failed(val cause: Throwable? = null) : PullRequestOpenCheckResult
}

/**
 * Maps a Bitbucket details lookup to a [PullRequestOpenCheckResult]. Extracted as a pure function so the mapping is
 * unit-testable without a Bitbucket client: a null lookup means [PullRequestOpenCheckResult.NotFound], an open state
 * means [PullRequestOpenCheckResult.StillOpen], and any other state means [PullRequestOpenCheckResult.NotOpen].
 */
object PullRequestOpenCheck {
    fun evaluate(details: PullRequestDetails?): PullRequestOpenCheckResult {
        if (details == null) {
            return PullRequestOpenCheckResult.NotFound
        }
        return when (details.summary.state) {
            PullRequestState.Open -> PullRequestOpenCheckResult.StillOpen(details = details)
            PullRequestState.Merged, PullRequestState.Declined -> PullRequestOpenCheckResult.NotOpen(state = details.summary.state)
        }
    }
}