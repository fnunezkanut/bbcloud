package com.github.fnunezkanut.bbcloud.bitbucket

import kotlin.time.Instant

enum class BuildState {
    Successful,
    Failed,
    InProgress,
    Stopped
}

/** [commitHash] is the full hash of the commit the status was reported for, or null when Bitbucket did not return it. */
data class PullRequestBuildStatus(
    val key: String,
    val name: String,
    val state: BuildState,
    val url: String,
    val description: String?,
    val updatedOn: Instant?,
    val commitHash: String? = null
)

data class BuildStatusPage(val statuses: List<PullRequestBuildStatus>, val nextUrl: String?)

/**
 * Data shown on the Overview tab; a null field means the corresponding request failed.
 */
data class PullRequestOverview(
    val commitsBehind: ItemCount?,
    val builds: List<PullRequestBuildStatus>?,
    val mergeChecks: List<PullRequestMergeCheck>? = null,
    val tasks: List<PullRequestTask>? = null,
    val conversation: PullRequestConversation? = null,
    val activityFeed: PullRequestActivityFeed? = null
)