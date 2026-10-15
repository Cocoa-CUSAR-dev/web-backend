package com.cocoa.web.service

import com.cocoa.web.model.Recurrence
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// Reference calendar (2026): Mon 5 Oct, Tue 6, Wed 7, Thu 8, Fri 9, Sat 10, Sun 11,
// Mon 12, Tue 13, Wed 14, Thu 15, Fri 16, Sat 17, Sun 18, Mon 19 Oct.
class RecurrenceScheduleTest {
    private fun recurrence(
        frequency: Recurrence.Frequency = Recurrence.Frequency.DAILY,
        weekdays: List<Int> = emptyList(),
        start: LocalDate = LocalDate.of(2026, 10, 5),
        until: LocalDate? = null,
    ) = Recurrence.Active(
        recurrenceId = UUID.randomUUID(),
        templateTaskId = UUID.randomUUID(),
        frequency = frequency,
        weekdays = weekdays,
        startDate = start,
        untilDate = until,
        createdBy = UUID.randomUUID(),
        templateOpenAt = LocalDateTime.of(2026, 10, 5, 8, 0),
        templateCloseAt = LocalDateTime.of(2026, 10, 5, 17, 0),
    )

    private fun d(day: Int) = LocalDate.of(2026, 10, day)

    @Test
    fun `weekday numbers follow ISO - Monday is 1 and Sunday is 7`() {
        assertTrue(RecurrenceSchedule.matches(Recurrence.Frequency.WEEKLY, listOf(1), d(5)))
        assertTrue(RecurrenceSchedule.matches(Recurrence.Frequency.WEEKLY, listOf(7), d(11)))
        assertFalse(RecurrenceSchedule.matches(Recurrence.Frequency.WEEKLY, listOf(1), d(6)))
        assertFalse(RecurrenceSchedule.matches(Recurrence.Frequency.WEEKLY, listOf(7), d(5)))
    }

    @Test
    fun `daily matches every day`() {
        (5..11).forEach { assertTrue(RecurrenceSchedule.matches(Recurrence.Frequency.DAILY, emptyList(), d(it))) }
    }

    @Test
    fun `a daily recurrence yields every day after the start up to today`() {
        val due = RecurrenceSchedule.dueDates(recurrence(), today = d(8))

        assertEquals(listOf(d(6), d(7), d(8)), due)
    }

    @Test
    fun `the start day is never listed - the template task is that round`() {
        assertEquals(emptyList<LocalDate>(), RecurrenceSchedule.dueDates(recurrence(), today = d(5)))
    }

    @Test
    fun `nothing is due before the start date`() {
        assertEquals(emptyList<LocalDate>(), RecurrenceSchedule.dueDates(recurrence(start = d(10)), today = d(8)))
    }

    @Test
    fun `a weekly recurrence on Monday and Thursday yields only those days`() {
        val due =
            RecurrenceSchedule.dueDates(
                recurrence(Recurrence.Frequency.WEEKLY, listOf(1, 4), start = d(5)),
                today = d(12),
            )

        // Thu 8 and Mon 12 -- the template itself covers Mon 5.
        assertEquals(listOf(d(8), d(12)), due)
    }

    @Test
    fun `a weekly day that falls outside the lookback window is not listed`() {
        val due =
            RecurrenceSchedule.dueDates(
                recurrence(Recurrence.Frequency.WEEKLY, listOf(1, 4), start = d(5)),
                today = d(15),
            )

        // Window is 9..15, so Thu 8 is too old; Mon 12 and Thu 15 remain.
        assertEquals(listOf(d(12), d(15)), due)
    }

    @Test
    fun `days after the end date are not listed, the end date itself is`() {
        val due = RecurrenceSchedule.dueDates(recurrence(until = d(8)), today = d(12))

        assertEquals(listOf(d(6), d(7), d(8)), due)
    }

    @Test
    fun `a finished recurrence yields nothing once its window has passed`() {
        // Ended Mon 5; today is 20 Oct, far past the lookback window.
        assertEquals(
            emptyList<LocalDate>(),
            RecurrenceSchedule.dueDates(recurrence(until = d(5)), today = LocalDate.of(2026, 10, 20)),
        )
    }

    @Test
    fun `a job that missed days opens the missed rounds, but only within the lookback window`() {
        // Started 5 Oct, job silent until 19 Oct. Lookback is 6 days: 13..19 only.
        val due = RecurrenceSchedule.dueDates(recurrence(), today = d(19))

        assertEquals((13..19).map { d(it) }, due)
        assertEquals(7, due.size)
    }

    @Test
    fun `the lookback window cannot reach back before the start date`() {
        val due = RecurrenceSchedule.dueDates(recurrence(start = d(8)), today = d(10))

        assertEquals(listOf(d(9), d(10)), due)
    }

    @Test
    fun `a round keeps the template's time of day`() {
        val (open, close) =
            RecurrenceSchedule.window(
                LocalDateTime.of(2026, 10, 5, 8, 30),
                LocalDateTime.of(2026, 10, 5, 17, 0),
                d(12),
            )

        assertEquals(LocalDateTime.of(2026, 10, 12, 8, 30), open)
        assertEquals(LocalDateTime.of(2026, 10, 12, 17, 0), close)
    }

    @Test
    fun `a round keeps the template's length, even across days`() {
        // Open Mon 08:00, close Wed 17:00 = 2 days 9 hours.
        val (open, close) =
            RecurrenceSchedule.window(
                LocalDateTime.of(2026, 10, 5, 8, 0),
                LocalDateTime.of(2026, 10, 7, 17, 0),
                d(12),
            )

        assertEquals(LocalDateTime.of(2026, 10, 12, 8, 0), open)
        assertEquals(LocalDateTime.of(2026, 10, 14, 17, 0), close)
    }
}
