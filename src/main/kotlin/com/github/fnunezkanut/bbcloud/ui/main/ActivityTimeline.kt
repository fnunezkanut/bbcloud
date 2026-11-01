package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityFeedEntry
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry

/**
 * Interleaves the activity entries and the comment threads of a pull request into one timeline, newest first. A thread
 * is placed at the time its root comment was written. Entries with the same time keep the activity entry first:
 * [sortedByDescending] is stable, and the activity entries come first in the concatenated list.
 *
 * Commit folding is not re-evaluated here. `PullRequestTimelineBuilder.buildActivityFeed` already folded the adjacent
 * commit pushes of one author, so a thread written between two of those pushes does not split the folded row.
 */
internal fun mergeActivityTimeline(activityEntries: List<ActivityFeedEntry>, threads: List<TimelineEntry.CommentThread>): List<TimelineEntry> {
    val entries: List<TimelineEntry> = activityEntries + threads
    return entries.sortedByDescending { entry -> entry.date }
}

/** One row of the Activity timeline: either an activity entry or a comment thread, and whether it closes the timeline. */
internal sealed interface ActivityRow {
    /** True only for the last row of the last day group, where the gutter line stops at the circle. */
    val isLast: Boolean

    data class Activity(val entry: ActivityFeedEntry, override val isLast: Boolean) : ActivityRow

    data class Thread(val thread: TimelineEntry.CommentThread, override val isLast: Boolean) : ActivityRow
}

/**
 * Turns one day group of the Activity timeline into the rows to draw, in order. [isLastGroup] tells whether the group is
 * the last one of the timeline, so only its last row is marked as the end of the gutter line.
 */
internal fun activityRows(group: HistoryDayGroup, isLastGroup: Boolean): List<ActivityRow> {
    val lastIndex = group.entries.lastIndex
    return group.entries.mapIndexed { index, entry ->
        val isLast = isLastGroup && index == lastIndex
        when (entry) {
            is ActivityFeedEntry -> ActivityRow.Activity(entry = entry, isLast = isLast)
            is TimelineEntry.CommentThread -> ActivityRow.Thread(thread = entry, isLast = isLast)
        }
    }
}