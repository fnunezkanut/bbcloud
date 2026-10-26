package com.github.fnunezkanut.bbcloud.bitbucket

/**
 * An open pull request the current user is involved in, together with the repository it belongs to. Both Other
 * Projects listings are built from it: the pull requests the user reviews, collected per repository, and the pull
 * requests the user opened, collected across the whole workspace.
 *
 * The repository is not part of [PullRequestSummary] because the Other Projects tab mixes several repositories in
 * one table, where the pull request id alone no longer identifies a row.
 */
data class ReviewingPullRequest(val summary: PullRequestSummary, val workspace: String, val repoSlug: String) {
    /** The repository slug (workspace is stripped). */
    fun repositoryName(): String {
        return repoSlug
    }
}

sealed class ReviewingPullRequestListResult {
    /**
     * [isTruncated] means the listing is knowingly incomplete: a repository could not be queried, the workspace
     * repository list ran past its page limit, or more pull requests came back than [pullRequests] holds. The panel
     * says so, rather than letting a short table pass for the whole review queue.
     */
    data class Success(val pullRequests: List<ReviewingPullRequest>, val isTruncated: Boolean = false) : ReviewingPullRequestListResult()

    data class Failed(val cause: Throwable? = null) : ReviewingPullRequestListResult()
}