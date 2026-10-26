package com.github.fnunezkanut.bbcloud.bitbucket

/**
 * The fields of a pull request to create. [description] is the raw Markdown body (a plain string on Bitbucket's write
 * side), [reviewerUuids] the explicit reviewers, [closeSourceBranch] maps to Bitbucket's `close_source_branch`, and
 * [isDraft] to `draft`.
 */
data class NewPullRequest(
    val title: String,
    val description: String,
    val sourceBranch: String,
    val destinationBranch: String,
    val reviewerUuids: List<String>,
    val closeSourceBranch: Boolean,
    val isDraft: Boolean
)

/** The branches a repository offers and its Bitbucket `mainbranch` name (null when the repository has none). */
data class NewPullRequestBranchData(val branches: RepositoryBranches, val mainBranchName: String?)

/** The outcome of creating a pull request. */
sealed interface CreatePullRequestResult {
    data class Created(val summary: PullRequestSummary) : CreatePullRequestResult

    data class Failed(val message: String? = null, val cause: Throwable? = null) : CreatePullRequestResult
}

/** The outcome of looking up an OPEN pull request for a given source and destination branch pair. */
sealed interface ExistingPullRequestLookupResult {
    data object None : ExistingPullRequestLookupResult

    data class Found(val summary: PullRequestSummary) : ExistingPullRequestLookupResult

    data class Failed(val cause: Throwable? = null) : ExistingPullRequestLookupResult
}