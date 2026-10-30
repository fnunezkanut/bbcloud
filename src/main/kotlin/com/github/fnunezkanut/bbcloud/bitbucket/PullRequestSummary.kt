package com.github.fnunezkanut.bbcloud.bitbucket

import kotlin.time.Instant

data class PullRequestSummary(
    val id: Long,
    val title: String,
    val authorDisplayName: String,
    val authorAvatarUrl: String?,
    val createdOn: Instant,
    val updatedOn: Instant,
    val sourceBranch: String,
    val destinationBranch: String,
    val commentCount: Int,
    val webUrl: String,
    val state: PullRequestState,
    val isDraft: Boolean,
    /** Bitbucket account uuid of the author (braces included, as Bitbucket returns it); null when the listing omitted it. */
    val authorUuid: String? = null,
    /** Account uuids of the participants who currently approve the pull request; empty when the listing omitted participants. */
    val approverUuids: Set<String> = emptySet()
)

sealed class PullRequestListResult {
    data class Success(val pullRequests: List<PullRequestSummary>) : PullRequestListResult()

    data class Failed(val cause: Throwable? = null) : PullRequestListResult()
}