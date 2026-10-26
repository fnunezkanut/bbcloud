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
    val isDraft: Boolean
)

sealed class PullRequestListResult {
    data class Success(val pullRequests: List<PullRequestSummary>) : PullRequestListResult()

    data class Failed(val cause: Throwable? = null) : PullRequestListResult()
}