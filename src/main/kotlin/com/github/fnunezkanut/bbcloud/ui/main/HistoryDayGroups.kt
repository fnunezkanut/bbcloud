package com.github.fnunezkanut.bbcloud.ui.main

import com.github.fnunezkanut.bbcloud.bitbucket.TimelineEntry
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

sealed interface HistoryDay {
    data object Today : HistoryDay

    data object Yesterday : HistoryDay

    data class Earlier(val date: LocalDate) : HistoryDay
}

data class HistoryDayGroup(val day: HistoryDay, val entries: List<TimelineEntry>)

object HistoryDayGroups {
    /** Keeps the order of [entries]; groups appear in the order of their first entry. */
    fun group(entries: List<TimelineEntry>, now: Instant, timeZone: TimeZone): List<HistoryDayGroup> {
        val today = now.toLocalDateTime(timeZone).date
        val yesterday = today.minus(value = 1, unit = DateTimeUnit.DAY)
        return entries
            .groupBy { entry -> entry.date.toLocalDateTime(timeZone).date }
            .map { (date, dayEntries) ->
                val day = when (date) {
                    today -> HistoryDay.Today
                    yesterday -> HistoryDay.Yesterday
                    else -> HistoryDay.Earlier(date)
                }
                HistoryDayGroup(day = day, entries = dayEntries)
            }
    }
}