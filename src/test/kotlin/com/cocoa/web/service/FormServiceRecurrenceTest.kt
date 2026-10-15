package com.cocoa.web.service

import com.cocoa.web.client.ChatbotClient
import com.cocoa.web.model.Form
import com.cocoa.web.model.Question
import com.cocoa.web.model.Recurrence
import com.cocoa.web.model.Reminder
import com.cocoa.web.model.Section
import com.cocoa.web.repository.FormRepository
import com.cocoa.web.repository.HandlerCatalogRepository
import com.cocoa.web.repository.QuestionRepository
import com.cocoa.web.repository.RecurrenceRepository
import com.cocoa.web.repository.SectionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// US4-4 (docs-and-plan#233): what createForm accepts for the new `recurrence`
// and `recipients` fields. A request that sets neither must behave exactly as
// it did before they existed.
class FormServiceRecurrenceTest {
    private val formRepository: FormRepository = mock()
    private val recurrenceRepository: RecurrenceRepository = mock()
    private val service =
        FormService(
            formRepository,
            mock<SectionRepository>(),
            mock<QuestionRepository>(),
            mock<HandlerCatalogRepository>(),
            mock<ChatbotClient>(),
            recurrenceRepository,
        )

    private val creator = UUID.randomUUID()
    private val formId = UUID.randomUUID()
    private val opens = LocalDateTime.of(2026, 10, 5, 8, 0)
    private val closes = LocalDateTime.of(2026, 10, 5, 17, 0)

    @BeforeEach
    fun stubRepository() {
        whenever(formRepository.createForm(any(), any(), any())).thenReturn(formId)
        whenever(formRepository.fetchForm(formId)).thenReturn(
            Form.Detail(formId, "Daily check", null, false, emptyList()),
        )
    }

    private fun request(
        recurrence: Recurrence.Request.Create? = null,
        recipients: List<Reminder.Recipient> = emptyList(),
        openAt: LocalDateTime = opens,
        closeAt: LocalDateTime = closes,
    ) = Form.Request.Create(
        title = "Daily check",
        description = null,
        openAt = openAt,
        closeAt = closeAt,
        handler = "farm_activity",
        sections =
            listOf(
                Section.Request.Create(
                    title = "Main",
                    description = null,
                    sortOrder = 1,
                    questions = listOf(Question.Request.Create("Note", null, "VARCHAR", "note", sortOrder = 1)),
                ),
            ),
        recurrence = recurrence,
        recipients = recipients,
    )

    private fun weekly(
        vararg weekdays: Int,
        until: LocalDate? = null,
    ) = Recurrence.Request.Create(Recurrence.Frequency.WEEKLY, weekdays.toList(), until)

    @Test
    fun `a request with no recurrence and no recipients is created exactly as before`() {
        val request = request()

        service.createForm(request, creator)

        verify(formRepository).createForm(request, creator, emptyList())
        verify(recurrenceRepository, never()).findUnknownRecipients(any())
        verify(recurrenceRepository, never()).resolveUserIds(any())
    }

    @Test
    fun `a daily recurrence is accepted and passed through`() {
        val request = request(Recurrence.Request.Create(Recurrence.Frequency.DAILY))

        service.createForm(request, creator)

        verify(formRepository).createForm(request, creator, emptyList())
    }

    @Test
    fun `a weekly recurrence on chosen days is accepted`() {
        val request = request(weekly(1, 4, until = LocalDate.of(2026, 12, 31)))

        service.createForm(request, creator)

        verify(formRepository).createForm(request, creator, emptyList())
    }

    @Test
    fun `an end date on the first day itself is accepted`() {
        service.createForm(request(weekly(1, until = LocalDate.of(2026, 10, 5))), creator)
    }

    @Test
    fun `a weekly recurrence with no weekdays is refused`() {
        val e = assertThrows<IllegalArgumentException> { service.createForm(request(weekly()), creator) }

        assertTrue(e.message!!.contains("weekday"))
        verify(formRepository, never()).createForm(any(), any(), any())
    }

    @Test
    fun `a weekday outside 1 to 7 is refused`() {
        assertThrows<IllegalArgumentException> { service.createForm(request(weekly(0, 1)), creator) }
        assertThrows<IllegalArgumentException> { service.createForm(request(weekly(8)), creator) }
        verify(formRepository, never()).createForm(any(), any(), any())
    }

    @Test
    fun `an end date before the first day is refused`() {
        assertThrows<IllegalArgumentException> {
            service.createForm(request(weekly(1, until = LocalDate.of(2026, 10, 4))), creator)
        }
        verify(formRepository, never()).createForm(any(), any(), any())
    }

    @Test
    fun `a repeating task must close after it opens`() {
        assertThrows<IllegalArgumentException> {
            service.createForm(
                request(Recurrence.Request.Create(Recurrence.Frequency.DAILY), closeAt = opens),
                creator,
            )
        }
        verify(formRepository, never()).createForm(any(), any(), any())
    }

    @Test
    fun `the open and close check applies only to repeating tasks`() {
        // Not this feature's business for a one-off task; behaviour stays as it was.
        service.createForm(request(closeAt = opens.minusHours(1)), creator)

        verify(formRepository).createForm(any(), eq(creator), eq(emptyList()))
    }

    @Test
    fun `recipients are resolved to people and those people are assigned`() {
        val roleRule = Reminder.Recipient(Reminder.RecipientType.ROLE, UUID.randomUUID())
        val people = listOf(UUID.randomUUID(), UUID.randomUUID())
        val request = request(recipients = listOf(roleRule))
        whenever(recurrenceRepository.findUnknownRecipients(listOf(roleRule))).thenReturn(emptyList())
        whenever(recurrenceRepository.resolveUserIds(listOf(roleRule))).thenReturn(people)

        service.createForm(request, creator)

        verify(formRepository).createForm(request, creator, people)
    }

    @Test
    fun `recipients work on a one-off task too, not only a repeating one`() {
        val userRule = Reminder.Recipient(Reminder.RecipientType.USER, UUID.randomUUID())
        val person = UUID.randomUUID()
        whenever(recurrenceRepository.findUnknownRecipients(any())).thenReturn(emptyList())
        whenever(recurrenceRepository.resolveUserIds(any())).thenReturn(listOf(person))

        service.createForm(request(recipients = listOf(userRule)), creator)

        verify(formRepository).createForm(any(), eq(creator), eq(listOf(person)))
    }

    @Test
    fun `a recipient that does not exist is refused and named`() {
        val ghost = Reminder.Recipient(Reminder.RecipientType.USER, UUID.randomUUID())
        whenever(recurrenceRepository.findUnknownRecipients(listOf(ghost))).thenReturn(listOf(ghost))

        val e = assertThrows<IllegalArgumentException> { service.createForm(request(recipients = listOf(ghost)), creator) }

        assertTrue(e.message!!.contains(ghost.id.toString()))
        verify(formRepository, never()).createForm(any(), any(), any())
    }

    @Test
    fun `recipients that match nobody are refused instead of publishing the task to everyone`() {
        val emptyRole = Reminder.Recipient(Reminder.RecipientType.ROLE, UUID.randomUUID())
        whenever(recurrenceRepository.findUnknownRecipients(any())).thenReturn(emptyList())
        whenever(recurrenceRepository.resolveUserIds(any())).thenReturn(emptyList())

        val e = assertThrows<IllegalArgumentException> { service.createForm(request(recipients = listOf(emptyRole)), creator) }

        assertEquals("The chosen recipients do not match any user", e.message)
        verify(formRepository, never()).createForm(any(), any(), any())
    }
}
