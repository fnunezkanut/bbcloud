package com.github.fnunezkanut.bbcloud.bitbucket

import kotlin.time.Instant

/** A pull request task; [content] is the raw markdown text. */
data class PullRequestTask(
    val id: Long,
    val content: String,
    val isResolved: Boolean,
    val createdOn: Instant? = null,
    val creator: ActivityUser? = null,
    val resolvedOn: Instant? = null,
    val resolvedBy: ActivityUser? = null
)

data class TaskPage(val tasks: List<PullRequestTask>, val nextUrl: String?)