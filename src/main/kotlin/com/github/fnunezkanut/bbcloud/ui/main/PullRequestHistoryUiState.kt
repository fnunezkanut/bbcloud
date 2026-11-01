package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestActivityFeed
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestConversation
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry

/**
 * Whether the activity feed and the conversation of the pull request have arrived. [Loading] until the first overview
 * is shown, [Error] when neither source could be loaded, and [Loaded] otherwise.
 */
enum class PullRequestHistoryLoadState {
    Loading,
    Loaded,
    Error
}

/**
 * The Compose state of the Activity card of the pull-request page (the class keeps its History name): the day-grouped
 * activity entries and comment threads, the load state, and the partial-failure and truncation notices.
 */
data class PullRequestHistoryUiState(
    val title: String = "",
    val loadState: PullRequestHistoryLoadState = PullRequestHistoryLoadState.Loading,
    val showPartialFailure: Boolean = false,
    val showTruncation: Boolean = false,
    val groups: List<HistoryDayGroup> = emptyList(),
    val avatarRevision: Int = 0
)

internal data class HistoryFeedRenderModel(val showPartialFailure: Boolean, val showTruncation: Boolean, val entries: List<TimelineEntry>)

/**
 * Builds what the Activity card draws from the activity feed and the conversation. Returns null only when both are
 * missing. A missing source is a partial failure: the other source is still drawn, and the notice is shown.
 */
internal fun computeRenderModel(feed: PullRequestActivityFeed?, conversation: PullRequestConversation?): HistoryFeedRenderModel? {
    if (feed == null && conversation == null) {
        return null
    }
    val isOneSourceMissing = (feed == null) != (conversation == null)
    return HistoryFeedRenderModel(
        showPartialFailure = isOneSourceMissing || feed?.hasPartialFailure == true,
        showTruncation = feed?.isTruncated == true || conversation?.isTruncated == true,
        entries = mergeActivityTimeline(
            activityEntries = feed?.entries.orEmpty(),
            threads = conversation?.threads.orEmpty()
        )
    )
}