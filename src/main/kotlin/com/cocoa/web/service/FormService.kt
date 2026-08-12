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
}
