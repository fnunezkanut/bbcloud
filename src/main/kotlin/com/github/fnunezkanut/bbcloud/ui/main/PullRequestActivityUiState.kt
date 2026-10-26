package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityFeedEntry
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestActivityFeed

/**
 * The Compose state of the activity feed card of the Changes tab: the day-grouped entries plus the partial-failure
 * and truncation notices of the feed.
 */
data class PullRequestActivityUiState(
    val title: String = "",
    val showPartialFailure: Boolean = false,
    val showTruncation: Boolean = false,
    val groups: List<ActivityDayGroup> = emptyList(),
    val avatarRevision: Int = 0
)

internal data class ActivityFeedRenderModel(val showPartialFailure: Boolean, val showTruncation: Boolean, val entries: List<ActivityFeedEntry>)

internal fun computeRenderModel(feed: PullRequestActivityFeed?): ActivityFeedRenderModel? {
    if (feed == null) return null
    return ActivityFeedRenderModel(
        showPartialFailure = feed.hasPartialFailure,
        showTruncation = feed.isTruncated,
        entries = feed.entries
    )
}