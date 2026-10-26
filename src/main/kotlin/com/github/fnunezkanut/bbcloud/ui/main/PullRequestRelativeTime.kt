package com.github.fnunezkanut.bbcloud.ui.main

import com.intellij.util.text.DateFormatUtil
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

object PullRequestRelativeTime {
    private val RELATIVE_CUTOFF = 7.days

    fun format(updatedOn: Instant): String {
        return DateFormatUtil.formatPrettyDateTime(Date(updatedOn.toEpochMilliseconds()))
    }

    /** Formats [date] relatively through seven days old, then uses the absolute pretty date-time format. */
    fun formatRelativeThenAbsolute(date: Instant, now: Instant = Clock.System.now()): String {
        val age = now - date
        return if (age <= RELATIVE_CUTOFF) {
            formatElapsed(date = date, now = now)
        } else {
            format(date)
        }
    }

    /** Formats [date] as the time elapsed since it, for example "5 minutes ago" or "3 days ago", at any age. */
    fun formatElapsed(date: Instant, now: Instant = Clock.System.now()): String {
        return DateFormatUtil.formatBetweenDates(date.toEpochMilliseconds(), now.toEpochMilliseconds())
    }
}