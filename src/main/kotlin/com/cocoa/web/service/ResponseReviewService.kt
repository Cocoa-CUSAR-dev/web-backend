package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.base.PageRequest
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.ResponseReview
import com.cocoa.web.repository.ResponseReviewRepository
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

// US2-8 (docs-and-plan#171/#172): the researcher's review-and-correct
// screen for chatbot submissions.
@Service
class ResponseReviewService(
    private val repository: ResponseReviewRepository,
) : BaseService() {
    private val logger = LoggerFactory.getLogger(this::class.java)

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

    // Corrects one field of one submission and returns it as the review
    // screen should now show it.
    //
    // Only the answer JSON on form.response changes. The domain rows the
    // mobile backend wrote from the same submission (harvest, farm_activity,
    // ...) are NOT touched here, so a corrected value can disagree with them
    // until that propagation is built.
    //
    // The log line is the stopgap record of who changed what, so nothing is
    // overwritten silently; the persistent audit trail is docs-and-plan#173.
    fun correctField(
        taskId: UUID,
        responseId: UUID,
        fieldName: String,
        request: ResponseReview.Request.Correct,
        correctedBy: UUID,
    ): ResponseReview.Field {
        val question =
            repository.fetchQuestions(taskId).firstOrNull { it.fieldName == fieldName }
                ?: throw IllegalArgumentException("Unknown field '$fieldName' for this task")
        require(AnswerCoercion.isEditable(question.inputType)) {
            "Fields of type ${question.inputType} cannot be corrected here"
        }

        val correction =
            repository.updateAnswerField(taskId, responseId, fieldName) { existing ->
                AnswerCoercion.coerce(question.inputType, request.value, existing)
            } ?: throw EntityNotFoundException("Response not found")

        logger.info(
            "response field corrected response_id={} task_id={} field={} old={} new={} corrected_by={} reason={}",
            responseId,
            taskId,
            fieldName,
            AnswerCoercion.display(correction.oldValue),
            AnswerCoercion.display(correction.newValue),
            correctedBy,
            request.reason?.takeIf { it.isNotBlank() },
        )

        val source = repository.fetchFieldSources(listOf(responseId))[responseId]?.get(fieldName)
        return toField(question, correction.newValue, source)
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
