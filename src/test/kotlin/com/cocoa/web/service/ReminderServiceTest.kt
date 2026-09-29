package com.cocoa.web.service

import com.cocoa.web.client.ChatbotClient
import com.cocoa.web.client.ChatbotReminder
import com.cocoa.web.exception.ChatbotUnavailableException
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.Form
import com.cocoa.web.model.Reminder
import com.cocoa.web.repository.FormRepository
import com.cocoa.web.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

class ReminderServiceTest {
    private val formRepository: FormRepository = mock()
    private val userRepository: UserRepository = mock()
    private val chatbotClient: ChatbotClient = mock()
    private val service = ReminderService(formRepository, userRepository, chatbotClient)

    private val formId = UUID.randomUUID()
    private val taskId = UUID.randomUUID()
    private val actingUser = UUID.randomUUID()
    private val roleId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        whenever(formRepository.findByFormId(formId)).thenReturn(
            Form.Entity(
                formId = formId,
                taskId = taskId,
                title = "t",
                content = "c",
                handler = "h",
                isMultipleSubmit = false,
                description = null,
                createdAt = LocalDateTime.now(),
            ),
        )
        whenever(userRepository.fetchRoleNames(any())).thenReturn(mapOf(roleId to "farmer"))
        whenever(userRepository.fetchUsernames(any())).thenReturn(mapOf(userId to "somchai"))
    }

    private fun schedule(
        isActive: Boolean = true,
        time: LocalTime = LocalTime.of(9, 0),
        recipients: List<Reminder.Recipient> = emptyList(),
    ) = ChatbotReminder(UUID.randomUUID(), taskId, "DAILY", time, isActive, actingUser, recipients)

    @Test
    fun `get returns a disabled empty detail when the task has no schedule`() {
        whenever(chatbotClient.listReminderSchedules(taskId)).thenReturn(emptyList())

        val detail = service.getReminder(formId)

        assertFalse(detail.enabled)
        assertNull(detail.scheduleId)
        assertTrue(detail.recipients.isEmpty())
    }

    @Test
    fun `get attaches readable names to the recipient ids`() {
        val recipients =
            listOf(
                Reminder.Recipient(Reminder.RecipientType.ROLE, roleId),
                Reminder.Recipient(Reminder.RecipientType.USER, userId),
            )
        whenever(chatbotClient.listReminderSchedules(taskId)).thenReturn(listOf(schedule(recipients = recipients)))

        val detail = service.getReminder(formId)

        assertTrue(detail.enabled)
        assertEquals(listOf("farmer", "somchai"), detail.recipients.map { it.label })
    }

    @Test
    fun `get prefers an active schedule over an inactive one`() {
        val inactive = schedule(isActive = false, time = LocalTime.of(7, 0))
        val active = schedule(isActive = true, time = LocalTime.of(18, 0))
        whenever(chatbotClient.listReminderSchedules(taskId)).thenReturn(listOf(inactive, active))

        assertEquals(LocalTime.of(18, 0), service.getReminder(formId).timeOfDay)
    }

    @Test
    fun `get on an unknown form is not found`() {
        whenever(formRepository.findByFormId(formId)).thenReturn(null)

        assertThrows(EntityNotFoundException::class.java) { service.getReminder(formId) }
    }

    @Test
    fun `save creates a schedule when enabled and none exists`() {
        val recipients = listOf(Reminder.Recipient(Reminder.RecipientType.ROLE, roleId))
        whenever(chatbotClient.listReminderSchedules(taskId)).thenReturn(emptyList())
        whenever(chatbotClient.createReminderSchedule(eq(taskId), any(), eq(actingUser), eq(recipients)))
            .thenReturn(schedule(recipients = recipients))

        val detail = service.saveReminder(formId, Reminder.Request.Create(true, LocalTime.of(9, 0), recipients), actingUser)

        assertTrue(detail.enabled)
        verify(chatbotClient).createReminderSchedule(taskId, LocalTime.of(9, 0), actingUser, recipients)
    }

    @Test
    fun `save updates the existing schedule when enabled and one exists`() {
        val existing = schedule(isActive = false)
        whenever(chatbotClient.listReminderSchedules(taskId)).thenReturn(listOf(existing))
        whenever(chatbotClient.updateReminderSchedule(any(), any(), any(), any()))
            .thenReturn(existing.copy(isActive = true, timeOfDay = LocalTime.of(10, 30)))

        val detail = service.saveReminder(formId, Reminder.Request.Create(true, LocalTime.of(10, 30)), actingUser)

        assertTrue(detail.enabled)
        verify(chatbotClient).updateReminderSchedule(existing.scheduleId, LocalTime.of(10, 30), true, emptyList())
        verify(chatbotClient, never()).createReminderSchedule(any(), any(), any(), any())
    }

    @Test
    fun `save with enabled false only switches the schedule off`() {
        val existing = schedule()
        whenever(chatbotClient.listReminderSchedules(taskId)).thenReturn(listOf(existing))
        whenever(chatbotClient.updateReminderSchedule(any(), any(), any(), any()))
            .thenReturn(existing.copy(isActive = false))

        val detail = service.saveReminder(formId, Reminder.Request.Create(false, LocalTime.of(9, 0)), actingUser)

        assertFalse(detail.enabled)
        verify(chatbotClient).updateReminderSchedule(existing.scheduleId, null, false, null)
    }

    @Test
    fun `save with enabled false and no schedule does nothing`() {
        whenever(chatbotClient.listReminderSchedules(taskId)).thenReturn(emptyList())

        val detail = service.saveReminder(formId, Reminder.Request.Create(false, LocalTime.of(9, 0)), actingUser)

        assertFalse(detail.enabled)
        verify(chatbotClient, never()).createReminderSchedule(any(), any(), any(), any())
        verify(chatbotClient, never()).updateReminderSchedule(any(), any(), any(), any())
    }

    @Test
    fun `save rejects an absurd number of recipients`() {
        val many = List(201) { Reminder.Recipient(Reminder.RecipientType.USER, UUID.randomUUID()) }

        assertThrows(IllegalArgumentException::class.java) {
            service.saveReminder(formId, Reminder.Request.Create(true, LocalTime.of(9, 0), many), actingUser)
        }
    }

    @Test
    fun `chatbot failure surfaces to the caller`() {
        whenever(chatbotClient.listReminderSchedules(taskId)).thenThrow(ChatbotUnavailableException("down"))

        assertThrows(ChatbotUnavailableException::class.java) { service.getReminder(formId) }
    }
}
