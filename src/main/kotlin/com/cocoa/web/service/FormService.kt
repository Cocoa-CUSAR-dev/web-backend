package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.client.ChatbotClient
import com.cocoa.web.exception.ChatbotUnavailableException
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.Form
import com.cocoa.web.model.Handler
import com.cocoa.web.model.Recurrence
import com.cocoa.web.model.Reminder
import com.cocoa.web.repository.FormRepository
import com.cocoa.web.repository.HandlerCatalogRepository
import com.cocoa.web.repository.QuestionRepository
import com.cocoa.web.repository.RecurrenceRepository
import com.cocoa.web.repository.SectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FormService(
    private val formRepository: FormRepository,
    private val sectionRepository: SectionRepository,
    private val questionRepository: QuestionRepository,
    private val handlerCatalogRepository: HandlerCatalogRepository,
    private val chatbotClient: ChatbotClient,
    private val recurrenceRepository: RecurrenceRepository,
) : BaseService() {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun getForms(): List<Form.Entity> {
        return formRepository.fetchForms()
    }

    fun getForm(formId: UUID): Form.Entity {
        return formRepository.findByFormId(formId)
            ?: throw EntityNotFoundException("Form not found")
    }

    fun getFormDetail(formId: UUID): Form.Detail {
        return formRepository.fetchForm(formId)
            ?: throw EntityNotFoundException("Form not found")
    }

    fun getFormByTaskId(taskId: UUID): Form.Entity {
        return formRepository.findByTaskId(taskId)
            ?: throw EntityNotFoundException("Form not found")
    }

    fun editForm(
        formId: UUID,
        request: Form.Request.Edit,
    ) {
        formRepository.findByFormId(formId)
            ?: throw EntityNotFoundException("Form not found")

        val questions = request.sections.flatMap { it.questions }
        sectionRepository.batchUpdate(request.sections)
        questionRepository.batchUpdate(questions)
    }

    fun getHandlers(): List<String> {
        return handlerCatalogRepository.listHandlers()
    }

    fun getHandlerFields(handler: String): List<Handler.Field> {
        return handlerCatalogRepository.fetchHandlerFields(handler)
    }

    // A form with no sections is invisible to fetchForms/GET-forms (it
    // filters on whereExists(SECTION)) -- 21 legacy rows already exist that
    // way by accident, per dynamic-form-proposal.md decision #5. Newly
    // created forms shouldn't silently repeat that: require at least one
    // section, and each section at least one question, so a created form is
    // always actually usable end to end.
    fun createForm(
        request: Form.Request.Create,
        createdBy: UUID,
    ): Form.Detail {
        require(request.title.isNotBlank()) { "Form title must not be blank" }
        require(request.handler.isNotBlank()) { "Form handler must not be blank" }
        require(request.sections.isNotEmpty()) { "A form must have at least one section" }
        request.sections.forEach {
            require(it.questions.isNotEmpty()) { "Section '${it.title}' must have at least one question" }
        }

        request.recurrence?.let { validateRecurrence(it, request) }
        val assigneeIds = resolveAssignees(request.recipients)

        val formId = formRepository.createForm(request, createdBy, assigneeIds)

        // Same permission as creating the form itself (create:form:all,
        // enforced at the controller) -- reminder settings are just part
        // of the create-form page, not a separately managed resource. A
        // failure to create the reminder must not fail the form creation
        // that already succeeded above -- see ChatbotClient's own
        // best-effort reasoning.
        if (request.reminder?.enabled == true) {
            val taskId =
                formRepository.findByFormId(formId)?.taskId
                    ?: throw IllegalStateException("Form was created but its task could not be found")
            try {
                chatbotClient.createReminderSchedule(
                    taskId,
                    request.reminder.timeOfDay,
                    createdBy,
                    request.reminder.recipients,
                )
            } catch (e: ChatbotUnavailableException) {
                logger.warn("form $formId was created but its reminder could not be: ${e.message}")
            }
        }

        return formRepository.fetchForm(formId)
            ?: throw IllegalStateException("Form was created but could not be re-fetched")
    }

    // US4-4 (docs-and-plan#233). The task being created is round #1, so its own
    // dates are what the later rounds are modelled on -- they have to make sense.
    private fun validateRecurrence(
        recurrence: Recurrence.Request.Create,
        request: Form.Request.Create,
    ) {
        require(request.closeAt.isAfter(request.openAt)) {
            "A repeating task must close after it opens"
        }
        if (recurrence.frequency == Recurrence.Frequency.WEEKLY) {
            val weekdays = recurrence.weekdays.orEmpty()
            require(weekdays.isNotEmpty()) { "A weekly task needs at least one weekday" }
            require(weekdays.all { it in 1..7 }) {
                "Weekdays must be numbers from 1 (Monday) to 7 (Sunday)"
            }
        }
        recurrence.untilDate?.let {
            require(!it.isBefore(request.openAt.toLocalDate())) {
                "The end date must not be before the day the task first opens"
            }
        }
    }

    // Turns the chosen recipients into the people they name. No recipients = no
    // assignment, i.e. the task stays visible to everyone as it always was. But
    // recipients that name nobody would silently do the same, so that is refused
    // instead of quietly publishing a task the researcher meant to restrict.
    private fun resolveAssignees(recipients: List<Reminder.Recipient>): List<UUID> {
        if (recipients.isEmpty()) return emptyList()

        val unknown = recurrenceRepository.findUnknownRecipients(recipients)
        require(unknown.isEmpty()) {
            "Unknown recipient: " + unknown.joinToString { "${it.type.name.lowercase()} ${it.id}" }
        }
        val assignees = recurrenceRepository.resolveUserIds(recipients)
        require(assignees.isNotEmpty()) { "The chosen recipients do not match any user" }
        return assignees
    }

    // Real edit (writes label/inputType/fieldName/sortOrder/description and
    // supports add/remove), as opposed to editForm's is_active/is_mandatory
    // toggle above. Same "at least one section, at least one question per
    // section" rule as createForm, so an update can't leave the form in the
    // same invisible-to-GET-forms state createForm already guards against.
    fun updateForm(
        formId: UUID,
        request: Form.Request.Update,
    ): Form.Detail {
        formRepository.findByFormId(formId)
            ?: throw EntityNotFoundException("Form not found")

        require(request.sections.isNotEmpty()) { "A form must have at least one section" }
        request.sections.forEach {
            require(it.questions.isNotEmpty()) { "Section '${it.title}' must have at least one question" }
        }

        formRepository.updateForm(formId, request)

        return formRepository.fetchForm(formId)
            ?: throw IllegalStateException("Form was updated but could not be re-fetched")
    }
}
