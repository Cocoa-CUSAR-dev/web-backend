package com.cocoa.web.service

import com.cocoa.web.client.ChatbotClient
import com.cocoa.web.client.ChatbotReminder
import com.cocoa.web.exception.ChatbotUnavailableException
import com.cocoa.web.model.Recurrence
import com.cocoa.web.model.Reminder
import com.cocoa.web.repository.RecurrenceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

// Reference calendar (2026): Mon 5 Oct ... Thu 8 Oct ... Mon 12 Oct.
class RecurrenceServiceTest {
    private val repository: RecurrenceRepository = mock()
    private val chatbot: ChatbotClient = mock()
    private val service = RecurrenceService(repository, chatbot)

    private val templateId = UUID.randomUUID()
    private val creator = UUID.randomUUID()
    private val templateOpen = LocalDateTime.of(2026, 10, 5, 8, 0)
    private val templateClose = LocalDateTime.of(2026, 10, 5, 17, 0)

    private fun d(day: Int) = LocalDate.of(2026, 10, day)

    private fun daily(
        templateTask: UUID = templateId,
        open: LocalDateTime? = templateOpen,
        close: LocalDateTime? = templateClose,
    ) = Recurrence.Active(
        recurrenceId = UUID.randomUUID(),
        templateTaskId = templateTask,
        frequency = Recurrence.Frequency.DAILY,
        weekdays = emptyList(),
        startDate = d(5),
        untilDate = null,
        createdBy = creator,
        templateOpenAt = open,
        templateCloseAt = close,
    )

    @BeforeEach
    fun quietDefaults() {
        whenever(repository.fetchRecipients(any())).thenReturn(emptyList())
        whenever(repository.fetchExistingPeriods(any(), any())).thenReturn(emptySet())
        whenever(chatbot.listReminderSchedules(any())).thenReturn(emptyList())
    }

    private fun running(vararg recurrences: Recurrence.Active) {
        whenever(repository.fetchRunning(any())).thenReturn(recurrences.toList())
    }

    @Test
    fun `looks back only as far as the lookback window`() {
        running()

        service.generateDueRounds(LocalDateTime.of(2026, 10, 12, 0, 5))

        verify(repository).fetchRunning(d(12).minusDays(RecurrenceSchedule.LOOKBACK_DAYS))
    }

    @Test
    fun `opens today's round with the template's time of day and length`() {
        val rec = daily()
        running(rec)
        val newTask = UUID.randomUUID()
        whenever(repository.createRound(any(), any(), any(), any(), any())).thenReturn(newTask)

        // Thu 8 Oct, 00:05. The lookback covers the 6th, 7th and 8th, but the rounds for the
        // 6th and 7th ended at 17:00 on their own days, so only today's is opened.
        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 0, 5))

        verify(repository).createRound(
            eq(rec),
            eq(d(8)),
            eq(LocalDateTime.of(2026, 10, 8, 8, 0)),
            eq(LocalDateTime.of(2026, 10, 8, 17, 0)),
            eq(emptyList()),
        )
        assertEquals(Recurrence.RunResult(created = 1, alreadyExisted = 0, skippedClosed = 2, skippedNoRecipients = 0, failed = 0), result)
    }

    @Test
    fun `does not create a round that already exists`() {
        val rec = daily()
        running(rec)
        whenever(repository.fetchExistingPeriods(eq(rec.recurrenceId), any())).thenReturn(setOf(d(8)))

        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 0, 5))

        verify(repository, never()).createRound(any(), any(), any(), any(), any())
        assertEquals(1, result.alreadyExisted)
        assertEquals(0, result.created)
    }

    @Test
    fun `a round another run created first is counted as already existing, with no reminder copy`() {
        running(daily())
        // The database's unique index said no: createRound returns null.
        whenever(repository.createRound(any(), any(), any(), any(), any())).thenReturn(null)

        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 0, 5))

        assertEquals(1, result.alreadyExisted)
        assertEquals(0, result.created)
        verify(chatbot, never()).listReminderSchedules(any())
    }

    @Test
    fun `a missed round whose window is already over is skipped, not opened`() {
        running(daily())
        whenever(repository.createRound(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID())

        // 20:00 on the 8th: today's 08:00-17:00 window is over too.
        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 20, 0))

        verify(repository, never()).createRound(any(), any(), any(), any(), any())
        assertEquals(3, result.skippedClosed)
        assertEquals(0, result.created)
    }

    @Test
    fun `a task with several days of length is still open on a later day`() {
        // Open Mon 08:00, close Wed 17:00 (2d 9h): the round for Tue 6th lasts until Thu 8th 17:00.
        val rec = daily(close = LocalDateTime.of(2026, 10, 7, 17, 0))
        running(rec)
        whenever(repository.createRound(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID())

        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 9, 0))

        // 6th, 7th and 8th are all still open at 09:00 on the 8th.
        assertEquals(3, result.created)
        assertEquals(0, result.skippedClosed)
    }

    @Test
    fun `assigns the people the template's recipients name`() {
        val rec = daily()
        running(rec)
        val roleId = UUID.randomUUID()
        val rules = listOf(Reminder.Recipient(Reminder.RecipientType.ROLE, roleId))
        val people = listOf(UUID.randomUUID(), UUID.randomUUID())
        whenever(repository.fetchRecipients(templateId)).thenReturn(rules)
        whenever(repository.resolveUserIds(rules)).thenReturn(people)
        whenever(repository.createRound(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID())

        service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 0, 5))

        verify(repository).createRound(eq(rec), eq(d(8)), any(), any(), eq(people))
    }

    @Test
    fun `recipients that match nobody open no round, rather than showing it to everyone`() {
        running(daily())
        val rules = listOf(Reminder.Recipient(Reminder.RecipientType.ROLE, UUID.randomUUID()))
        whenever(repository.fetchRecipients(templateId)).thenReturn(rules)
        whenever(repository.resolveUserIds(rules)).thenReturn(emptyList())

        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 0, 5))

        verify(repository, never()).createRound(any(), any(), any(), any(), any())
        assertEquals(3, result.skippedNoRecipients)
        assertEquals(0, result.failed)
    }

    @Test
    fun `an error on one round is counted and the remaining rounds still open`() {
        // A two-day-long template keeps the rounds for the 6th, 7th and 8th all open at 09:00 on the 8th.
        val rec = daily(close = LocalDateTime.of(2026, 10, 7, 17, 0))
        running(rec)
        whenever(repository.createRound(eq(rec), eq(d(6)), any(), any(), any())).thenThrow(IllegalStateException("db down"))
        whenever(repository.createRound(eq(rec), eq(d(7)), any(), any(), any())).thenReturn(UUID.randomUUID())
        whenever(repository.createRound(eq(rec), eq(d(8)), any(), any(), any())).thenReturn(UUID.randomUUID())

        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 9, 0))

        assertEquals(2, result.created)
        assertEquals(1, result.failed)
    }

    @Test
    fun `a failing recurrence does not stop the next one`() {
        val broken = daily(templateTask = UUID.randomUUID())
        val healthy = daily(templateTask = templateId)
        running(broken, healthy)
        whenever(repository.fetchRecipients(broken.templateTaskId)).thenThrow(IllegalStateException("boom"))
        whenever(repository.createRound(eq(healthy), any(), any(), any(), any())).thenReturn(UUID.randomUUID())

        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 0, 5))

        assertEquals(1, result.failed)
        assertEquals(1, result.created)
    }

    @Test
    fun `a template with no open or close time is reported as failed`() {
        running(daily(open = null, close = null))

        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 0, 5))

        assertEquals(1, result.failed)
        verify(repository, never()).createRound(any(), any(), any(), any(), any())
    }

    @Test
    fun `nothing due means nothing is read or written beyond the list of recurrences`() {
        // Started today: the template itself is today's round.
        running(daily().copy(startDate = d(8)))

        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 0, 5))

        assertEquals(Recurrence.RunResult(0, 0, 0, 0, 0), result)
        verify(repository, never()).fetchRecipients(any())
        verify(repository, never()).createRound(any(), any(), any(), any(), any())
    }

    @Test
    fun `copies the template's active reminders onto each new round, and only those`() {
        val rec = daily()
        running(rec)
        val newTask = UUID.randomUUID()
        whenever(repository.createRound(any(), any(), any(), any(), any())).thenReturn(newTask)
        val recipients = listOf(Reminder.Recipient(Reminder.RecipientType.USER, UUID.randomUUID()))
        whenever(chatbot.listReminderSchedules(templateId)).thenReturn(
            listOf(
                reminder(LocalTime.of(9, 30), active = true, recipients = recipients),
                reminder(LocalTime.of(18, 0), active = false),
            ),
        )

        service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 0, 5))

        verify(chatbot).createReminderSchedule(newTask, LocalTime.of(9, 30), creator, recipients)
        verify(chatbot, times(1)).createReminderSchedule(any(), any(), any(), any())
    }

    @Test
    fun `the template's reminders are read once per recurrence however many rounds open`() {
        val rec = daily(close = LocalDateTime.of(2026, 10, 7, 17, 0))
        running(rec)
        whenever(repository.createRound(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID())
        whenever(chatbot.listReminderSchedules(templateId)).thenReturn(listOf(reminder(LocalTime.of(9, 30), active = true)))

        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 9, 0))

        assertEquals(3, result.created)
        verify(chatbot, times(1)).listReminderSchedules(templateId)
        verify(chatbot, times(3)).createReminderSchedule(any(), any(), any(), any())
    }

    @Test
    fun `a chatbot outage costs the reminders but never the round`() {
        running(daily())
        whenever(repository.createRound(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID())
        whenever(chatbot.listReminderSchedules(any())).thenThrow(ChatbotUnavailableException("down"))

        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 0, 5))

        assertEquals(1, result.created)
        assertEquals(0, result.failed)
        verify(chatbot, never()).createReminderSchedule(any(), any(), any(), any())
    }

    @Test
    fun `failing to create one reminder does not stop the round or the rest`() {
        running(daily())
        whenever(repository.createRound(any(), any(), any(), any(), any())).thenReturn(UUID.randomUUID())
        whenever(chatbot.listReminderSchedules(templateId)).thenReturn(
            listOf(reminder(LocalTime.of(9, 0), active = true), reminder(LocalTime.of(10, 0), active = true)),
        )
        whenever(chatbot.createReminderSchedule(any(), eq(LocalTime.of(9, 0)), any(), any())).thenThrow(ChatbotUnavailableException("down"))

        val result = service.generateDueRounds(LocalDateTime.of(2026, 10, 8, 0, 5))

        assertEquals(1, result.created)
        verify(chatbot).createReminderSchedule(any(), eq(LocalTime.of(10, 0)), any(), any())
    }

    private fun reminder(
        at: LocalTime,
        active: Boolean,
        recipients: List<Reminder.Recipient> = emptyList(),
    ) = ChatbotReminder(
        scheduleId = UUID.randomUUID(),
        taskId = templateId,
        cadence = "DAILY",
        timeOfDay = at,
        isActive = active,
        createdBy = creator,
        recipients = recipients,
    )
}
