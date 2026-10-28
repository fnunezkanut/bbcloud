package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityFeedEntry
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestActivityFeed

/**
 * The Compose state of the History card of the Overview tab: the day-grouped entries plus the partial-failure
 * and truncation notices of the feed.
 */
data class PullRequestHistoryUiState(
    val title: String = "",
    val showPartialFailure: Boolean = false,
    val showTruncation: Boolean = false,
    val groups: List<HistoryDayGroup> = emptyList(),
    val avatarRevision: Int = 0
)

internal data class HistoryFeedRenderModel(val showPartialFailure: Boolean, val showTruncation: Boolean, val entries: List<ActivityFeedEntry>)

internal fun computeRenderModel(feed: PullRequestActivityFeed?): HistoryFeedRenderModel? {
    if (feed == null) return null
    return HistoryFeedRenderModel(
        showPartialFailure = feed.hasPartialFailure,
        showTruncation = feed.isTruncated,
        entries = feed.entries
    )
}