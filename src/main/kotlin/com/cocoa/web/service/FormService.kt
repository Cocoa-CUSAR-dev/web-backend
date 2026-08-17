package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.Form
import com.cocoa.web.model.Handler
import com.cocoa.web.repository.FormRepository
import com.cocoa.web.repository.HandlerCatalogRepository
import com.cocoa.web.repository.QuestionRepository
import com.cocoa.web.repository.SectionRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FormService(
    private val formRepository: FormRepository,
    private val sectionRepository: SectionRepository,
    private val questionRepository: QuestionRepository,
    private val handlerCatalogRepository: HandlerCatalogRepository,
) : BaseService() {
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
