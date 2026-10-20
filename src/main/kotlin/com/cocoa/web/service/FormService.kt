package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.client.ChatbotClient
import com.cocoa.web.exception.ChatbotUnavailableException
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.Form
import com.cocoa.web.model.Handler
import com.cocoa.web.repository.FormRepository
import com.cocoa.web.repository.HandlerCatalogRepository
import com.cocoa.web.repository.HandlerRegistryRepository
import com.cocoa.web.repository.QuestionRepository
import com.cocoa.web.repository.SectionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class FormService(
    private val formRepository: FormRepository,
    private val sectionRepository: SectionRepository,
    private val questionRepository: QuestionRepository,
    private val handlerCatalogRepository: HandlerCatalogRepository,
    private val handlerRegistryRepository: HandlerRegistryRepository,
    private val chatbotClient: ChatbotClient,
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

    // GET /forms/handlers/catalog (the Data Map page). Everything here
    // changes slowly -- registry rows by migration, counts as forms and
    // responses trickle in -- so it's cached 5 minutes like the registry
    // itself rather than re-aggregated on every page view.
    @Volatile
    private var catalogCache: Pair<Instant, Handler.Catalog.Response>? = null

    fun getHandlerCatalog(): Handler.Catalog.Response {
        catalogCache?.let { (loadedAt, catalog) ->
            if (Duration.between(loadedAt, Instant.now()) < HandlerRegistryRepository.TTL) return catalog
        }

        val stats = handlerCatalogRepository.fetchCatalogStats()
        val entries =
            handlerRegistryRepository.activeHandlers().map { registration ->
                val usage = stats.fieldUsage[registration.handler].orEmpty()
                Handler.Catalog.Entry(
                    handler = registration.handler,
                    labelTh = registration.labelTh,
                    descriptionTh = registration.descriptionTh,
                    category = registration.category,
                    frequency = registration.frequency,
                    table = registration.qualifiedTable,
                    parentHandler = registration.parentHandler,
                    parentField = registration.parentField,
                    fields =
                        handlerCatalogRepository.fetchHandlerFields(registration.handler).map { field ->
                            Handler.Catalog.CatalogField(
                                name = field.name,
                                dataType = field.dataType,
                                required = field.required,
                                references = field.references,
                                dependsOn = field.dependsOn,
                                allowedInputTypes = field.allowedInputTypes,
                                usedByForms = usage[field.name] ?: 0,
                            )
                        },
                    forms = stats.formSummaries[registration.handler] ?: Handler.Catalog.FormSummary(0, null, null),
                    responseCount = stats.responseCounts[registration.handler] ?: 0,
                )
            }

        val catalog =
            Handler.Catalog.Response(
                handlers = entries,
                dependencies = handlerRegistryRepository.dependencies(),
                registrationTables = HandlerCatalogRepository.REGISTRATION_TABLES,
                fieldLabels = stats.fieldLabels,
            )
        catalogCache = Instant.now() to catalog
        return catalog
    }

    // US2-13: the structural rules every saved form must pass (see
    // FormDefinitionValidator). Throws IllegalArgumentException -> 400 with
    // every problem listed, one per line.
    private fun validateDefinition(
        handler: String,
        questions: List<FormDefinitionValidator.QuestionSpec>,
    ) {
        val registration =
            handlerCatalogRepository.findRegistration(handler)
                ?: throw IllegalArgumentException("Unknown handler: $handler")
        val errors =
            FormDefinitionValidator.validate(
                registration = registration,
                fields = handlerCatalogRepository.fetchHandlerFields(handler),
                dependencies = handlerRegistryRepository.dependencies(),
                questions = questions,
            )
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException(errors.joinToString("\n"))
        }
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
        validateDefinition(
            request.handler,
            request.sections.flatMap { section ->
                section.questions.map { question ->
                    FormDefinitionValidator.QuestionSpec(
                        sectionOrder = section.sortOrder,
                        questionOrder = question.sortOrder,
                        label = question.label,
                        inputType = question.inputType,
                        fieldName = question.fieldName,
                    )
                }
            },
        )

        val formId = formRepository.createForm(request)

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

    // Real edit (writes label/inputType/fieldName/sortOrder/description and
    // supports add/remove), as opposed to editForm's is_active/is_mandatory
    // toggle above. Same "at least one section, at least one question per
    // section" rule as createForm, so an update can't leave the form in the
    // same invisible-to-GET-forms state createForm already guards against.
    fun updateForm(
        formId: UUID,
        request: Form.Request.Update,
    ): Form.Detail {
        val form =
            formRepository.findByFormId(formId)
                ?: throw EntityNotFoundException("Form not found")

        require(request.sections.isNotEmpty()) { "A form must have at least one section" }
        request.sections.forEach {
            require(it.questions.isNotEmpty()) { "Section '${it.title}' must have at least one question" }
        }
        // Only what stays active counts -- an inactive question is never
        // asked, so it can't satisfy a required column. The handler can't
        // change on update, so it comes from the stored form.
        validateDefinition(
            form.handler,
            request.sections.filter { it.isActive }.flatMap { section ->
                section.questions.filter { it.isActive }.map { question ->
                    FormDefinitionValidator.QuestionSpec(
                        sectionOrder = section.sortOrder,
                        questionOrder = question.sortOrder,
                        label = question.label,
                        inputType = question.inputType,
                        fieldName = question.fieldName,
                    )
                }
            },
        )

        formRepository.updateForm(formId, request)

        return formRepository.fetchForm(formId)
            ?: throw IllegalStateException("Form was updated but could not be re-fetched")
    }
}
