package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@Tag("unit")
class HistoryDayGroupsTest {
    private val now = Instant.parse("2026-09-30T22:30:00Z")

    @Test
    fun `group - should label today, yesterday, and earlier days in order`() {
        // GIVEN
        val today = opened("2026-09-30T08:00:00Z")
        val yesterdayLate = opened("2026-09-29T23:00:00Z")
        val yesterdayEarly = opened("2026-09-29T01:00:00Z")
        val earlier = opened("2026-09-20T12:00:00Z")

        // WHEN
        val groups = HistoryDayGroups.group(entries = listOf(today, yesterdayLate, yesterdayEarly, earlier), now = now, timeZone = TimeZone.UTC)

        // THEN
        groups shouldBe listOf(
            HistoryDayGroup(day = HistoryDay.Today, entries = listOf(today)),
            HistoryDayGroup(day = HistoryDay.Yesterday, entries = listOf(yesterdayLate, yesterdayEarly)),
            HistoryDayGroup(day = HistoryDay.Earlier(LocalDate(year = 2026, month = 9, day = 20)), entries = listOf(earlier))
        )
    }

    @Test
    fun `group - should use the given time zone for day boundaries`() {
        // GIVEN
        val entry = opened("2026-09-30T23:30:00Z")

        // WHEN
        val groups = HistoryDayGroups.group(entries = listOf(entry), now = now, timeZone = TimeZone.of("Europe/London"))

        // THEN
        groups shouldBe listOf(HistoryDayGroup(day = HistoryDay.Earlier(LocalDate(year = 2026, month = 10, day = 1)), entries = listOf(entry)))
    }

    @Test
    fun `group - should return no groups for no entries`() {
        // WHEN
        val groups = HistoryDayGroups.group(entries = emptyList(), now = now, timeZone = TimeZone.UTC)

        // THEN
        groups.shouldBeEmpty()
    }

    private fun opened(date: String): TimelineEntry.Opened {
        return TimelineEntry.Opened(date = Instant.parse(date), actor = null)
    }
}