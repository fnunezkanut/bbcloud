package com.github.fnunezkanut.bbcloud.bitbucket

/**
 * Pure state check for whether a pull-request editor tab should take over its page with the
 * "no longer Open" view.
 *
 * A pull request triggers the takeover when its state is anything other than [PullRequestState.Open].
 * Draft pull requests are [PullRequestState.Open] with [PullRequestSummary.isDraft] set to true, so they
 * never trigger the takeover — the user can still interact with them normally.
 *
 * Extracted as a standalone object so the decision is unit-testable without constructing the editor session.
 */
object PullRequestNotOpenCheck {
    fun check(summary: PullRequestSummary): Boolean {
        return summary.state != PullRequestState.Open
    }
}