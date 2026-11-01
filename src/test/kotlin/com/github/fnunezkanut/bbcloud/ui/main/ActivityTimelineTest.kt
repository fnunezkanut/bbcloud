package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.ActivityFeedEntry
import com.github.fnunezkanut.bbcloud.bitbucket.ActivityUser
import com.github.fnunezkanut.bbcloud.bitbucket.PullRequestComment
import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class ActivityTimelineTest {
    private val author = ActivityUser(uuid = "{author}", displayName = "Author", avatarUrl = null)
    private var lastId = 0L

    @Test
    fun `mergeActivityTimeline - should interleave threads between activity entries by time`() {
        // GIVEN an approval at 12:00, a thread at 11:00 and an opening at 10:00
        val approved = approved(date = Instant.parse("2026-09-30T12:00:00Z"))
        val thread = thread(date = Instant.parse("2026-09-30T11:00:00Z"))
        val opened = opened(date = Instant.parse("2026-09-30T10:00:00Z"))

        // WHEN
        val timeline = mergeActivityTimeline(activityEntries = listOf(approved, opened), threads = listOf(thread))

        // THEN newest first, with the thread between the two entries
        timeline shouldBe listOf(approved, thread, opened)
    }

    @Test
    fun `mergeActivityTimeline - should keep the activity entry first when times are equal`() {
        // GIVEN an approval and a thread written at the same instant
        val instant = Instant.parse("2026-09-30T11:00:00Z")
        val approved = approved(date = instant)
        val thread = thread(date = instant)

        // WHEN
        val timeline = mergeActivityTimeline(activityEntries = listOf(approved), threads = listOf(thread))

        // THEN
        timeline shouldBe listOf(approved, thread)
    }

    @Test
    fun `mergeActivityTimeline - should return only activity entries when there are no threads`() {
        // GIVEN
        val approved = approved(date = Instant.parse("2026-09-30T12:00:00Z"))
        val opened = opened(date = Instant.parse("2026-09-30T10:00:00Z"))

        // WHEN
        val timeline = mergeActivityTimeline(activityEntries = listOf(approved, opened), threads = emptyList())

        // THEN
        timeline shouldBe listOf(approved, opened)
    }

    @Test
    fun `mergeActivityTimeline - should return only threads when there are no activity entries`() {
        // GIVEN
        val newer = thread(date = Instant.parse("2026-09-30T12:00:00Z"))
        val older = thread(date = Instant.parse("2026-09-30T10:00:00Z"))

        // WHEN
        val timeline = mergeActivityTimeline(activityEntries = emptyList(), threads = listOf(older, newer))

        // THEN
        timeline shouldBe listOf(newer, older)
    }

    @Test
    fun `activityRows - should mark only the last row of the last group as last`() {
        // GIVEN two rows in the last group
        val first = approved(date = Instant.parse("2026-09-30T12:00:00Z"))
        val second = thread(date = Instant.parse("2026-09-30T11:00:00Z"))
        val group = HistoryDayGroup(day = HistoryDay.Today, entries = listOf(first, second))

        // WHEN
        val rows = activityRows(group = group, isLastGroup = true)

        // THEN
        assertSoftly {
            rows.map { row -> row.isLast } shouldBe listOf(false, true)
        }
    }

    @Test
    fun `activityRows - should map threads and activity entries in order`() {
        // GIVEN
        val approved = approved(date = Instant.parse("2026-09-30T12:00:00Z"))
        val thread = thread(date = Instant.parse("2026-09-30T11:00:00Z"))
        val group = HistoryDayGroup(day = HistoryDay.Today, entries = listOf(approved, thread))

        // WHEN
        val rows = activityRows(group = group, isLastGroup = true)

        // THEN
        assertSoftly {
            rows shouldBe listOf(
                ActivityRow.Activity(entry = approved, isLast = false),
                ActivityRow.Thread(thread = thread, isLast = true)
            )
        }
    }

    @Test
    fun `activityRows - should not mark the last row of an earlier group as last`() {
        // GIVEN a group that is followed by another day
        val approved = approved(date = Instant.parse("2026-09-29T12:00:00Z"))
        val group = HistoryDayGroup(day = HistoryDay.Earlier(LocalDate.parse("2026-09-29")), entries = listOf(approved))

        // WHEN
        val rows = activityRows(group = group, isLastGroup = false)

        // THEN
        rows.map { row -> row.isLast } shouldBe listOf(false)
    }

    private fun opened(date: Instant): TimelineEntry.Opened {
        return TimelineEntry.Opened(date = date, actor = author)
    }

    private fun approved(date: Instant): ActivityFeedEntry {
        return TimelineEntry.Approved(date = date, actor = author)
    }

    private fun thread(date: Instant): TimelineEntry.CommentThread {
        return TimelineEntry.CommentThread(root = comment(date = date), replies = emptyList())
    }

    private fun comment(date: Instant): PullRequestComment {
        return PullRequestComment(
            id = nextId(),
            parentId = null,
            createdOn = date,
            author = author,
            html = "<p>Comment</p>",
            inlinePath = null,
            inlineLine = null,
            inlineSide = null,
            isResolved = false,
            isDeleted = false,
            isPending = false
        )
    }

    private fun nextId(): Long {
        lastId++
        return lastId
    }
}