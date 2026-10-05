package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.base.PageRequest
import com.cocoa.web.model.ResponseReview
import com.cocoa.web.repository.ResponseReviewRepository
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import java.util.UUID

// US2-8 (docs-and-plan#171/#172): the researcher's review-and-correct
// screen for chatbot submissions.
@Service
class ResponseReviewService(
    private val repository: ResponseReviewRepository,
) : BaseService() {
    fun getReview(
        taskId: UUID,
        aiOnly: Boolean,
        pageRequest: PageRequest = PageRequest(),
    ): List<ResponseReview.Submission> {
        val rows = repository.fetchSubmissions(taskId, aiOnly, pageRequest)
        if (rows.isEmpty()) return emptyList()

        val questions = repository.fetchQuestions(taskId)
        val sources = repository.fetchFieldSources(rows.map { it.responseId })

        return rows.map { row ->
            ResponseReview.Submission(
                responseId = row.responseId,
                submitter = row.submitter,
                submittedAt = row.submittedAt,
                fields =
                    questions.map { question ->
                        toField(
                            question = question,
                            stored = row.answer?.get(question.fieldName),
                            source = sources[row.responseId]?.get(question.fieldName),
                        )
                    },
            )
        }
    }

    private fun toField(
        question: ResponseReviewRepository.QuestionMeta,
        stored: JsonNode?,
        source: String?,
    ) = ResponseReview.Field(
        fieldName = question.fieldName,
        label = question.label,
        inputType = question.inputType,
        value = AnswerCoercion.display(stored),
        source = source,
        editable = AnswerCoercion.isEditable(question.inputType),
    )
}
