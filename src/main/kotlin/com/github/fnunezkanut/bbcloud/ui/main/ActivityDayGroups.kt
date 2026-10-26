package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

sealed interface ActivityDay {
    data object Today : ActivityDay

    data object Yesterday : ActivityDay

    data class Earlier(val date: LocalDate) : ActivityDay
}

data class ActivityDayGroup(val day: ActivityDay, val entries: List<TimelineEntry>)

object ActivityDayGroups {
    /** Keeps the order of [entries]; groups appear in the order of their first entry. */
    fun group(entries: List<TimelineEntry>, now: Instant, timeZone: TimeZone): List<ActivityDayGroup> {
        val today = now.toLocalDateTime(timeZone).date
        val yesterday = today.minus(value = 1, unit = DateTimeUnit.DAY)
        return entries
            .groupBy { entry -> entry.date.toLocalDateTime(timeZone).date }
            .map { (date, dayEntries) ->
                val day = when (date) {
                    today -> ActivityDay.Today
                    yesterday -> ActivityDay.Yesterday
                    else -> ActivityDay.Earlier(date)
                }
                ActivityDayGroup(day = day, entries = dayEntries)
            }
    }
}