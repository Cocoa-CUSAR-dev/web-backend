package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.base.PageRequest
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.Diary
import com.cocoa.web.model.FormResponse
import com.cocoa.web.repository.FormResponseRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class FormResponseService(
    private val formResponseRepository: FormResponseRepository,
) : BaseService() {
    fun getFormResponses(taskId: UUID): List<FormResponse.Entity> {
        return formResponseRepository.fetchTaskResponses(taskId)
    }

    fun getFormResponse(
        taskId: UUID,
        formResponseId: UUID,
    ): FormResponse.Entity {
        return formResponseRepository.fetchTaskResponse(taskId, formResponseId)
            ?: throw EntityNotFoundException("Form Response Not Found")
    }

    fun getUserResponse(
        taskId: UUID,
        pageRequest: PageRequest = PageRequest(),
    ): List<FormResponse.Detail> {
        return formResponseRepository.fetchUserResponses(taskId, pageRequest)
    }

    // US2-6/US5-1 (docs-and-plan#130)
    fun getOwnSubmissionDays(userId: UUID): List<LocalDate> {
        return formResponseRepository.fetchOwnSubmissionDays(userId)
    }

    fun getOwnAnswersForDate(
        userId: UUID,
        date: LocalDate,
    ): List<Diary.AnswerField> {
        return formResponseRepository.fetchAnswerFieldsForUserAndDate(userId, date)
    }
}
