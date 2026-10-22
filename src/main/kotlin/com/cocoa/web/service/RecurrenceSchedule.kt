package com.cocoa.web.service

import com.cocoa.web.model.Recurrence
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The calendar side of recurring tasks, kept free of any database or clock so
 * every rule can be tested with plain dates. "Day" always means the farmer's
 * day (Asia/Bangkok); the caller passes today's date in that zone.
 */
object RecurrenceSchedule {
    /**
     * How many days back a round that was missed (the job did not run, or ran
     * late) is still opened. Beyond this the round is simply skipped: a round
     * that old is long over, and the window keeps one bad week of downtime from
     * opening a pile of dead tasks.
     */
    const val LOOKBACK_DAYS = 6L

    fun matches(
        frequency: Recurrence.Frequency,
        weekdays: List<Int>,
        date: LocalDate,
    ): Boolean =
        when (frequency) {
            Recurrence.Frequency.DAILY -> true
            // ISO numbering: Monday = 1 ... Sunday = 7, same as the weekdays column.
            Recurrence.Frequency.WEEKLY -> date.dayOfWeek.value in weekdays
        }

    /**
     * The days a round should exist for as of [today], oldest first.
     *
     * The start date itself is never listed: the template task IS that round.
     * Days after the end date are never listed, and neither is the future.
     */
    fun dueDates(
        recurrence: Recurrence.Active,
        today: LocalDate,
    ): List<LocalDate> {
        val first = maxOf(recurrence.startDate.plusDays(1), today.minusDays(LOOKBACK_DAYS))
        val last = recurrence.untilDate?.let { minOf(it, today) } ?: today
        if (first.isAfter(last)) return emptyList()

        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            .filter { matches(recurrence.frequency, recurrence.weekdays, it) }
            .toList()
    }

    /**
     * Open and close time of the round for [date]: the template's time of day
     * and the template's length (so a task open Mon 08:00 to Wed 17:00 stays a
     * two-day-nine-hour task every round).
     */
    fun window(
        templateOpenAt: LocalDateTime,
        templateCloseAt: LocalDateTime,
        date: LocalDate,
    ): Pair<LocalDateTime, LocalDateTime> {
        val open = date.atTime(templateOpenAt.toLocalTime())
        return open to open.plus(Duration.between(templateOpenAt, templateCloseAt))
    }
}
