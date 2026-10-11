package com.cocoa.web.service

import com.cocoa.web.base.PageRequest
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.ResponseReview
import com.cocoa.web.repository.ResponseCorrectionLogRepository
import com.cocoa.web.repository.ResponseReviewRepository
import com.cocoa.web.repository.ResponseReviewRepository.QuestionMeta
import com.cocoa.web.repository.ResponseReviewRepository.SubmissionRow
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.UUID

class ResponseReviewServiceTest {
    private val repository: ResponseReviewRepository = mock()
    private val correctionLog: ResponseCorrectionLogRepository = mock()
    private val service = ResponseReviewService(repository, correctionLog)

    private val taskId = UUID.randomUUID()
    private val responseId = UUID.randomUUID()
    private val page = PageRequest()

    private val questions =
        listOf(
            QuestionMeta("fan_count", "Number of fans", "INT"),
            QuestionMeta("note", "Note", "VARCHAR"),
            QuestionMeta("farm_id", "Farm", "OPTION"),
        )

    private fun row(answerJson: Map<String, Any?>?) =
        SubmissionRow(
            responseId = responseId,
            submitter = "Somchai Jaidee",
            submittedAt = LocalDateTime.of(2026, 9, 27, 10, 0),
            answer =
                answerJson?.let { map ->
                    JsonNodeFactory.instance.objectNode().also { obj ->
                        map.forEach { (k, v) ->
                            when (v) {
                                null -> obj.putNull(k)
                                is Int -> obj.put(k, v)
                                else -> obj.put(k, v.toString())
                            }
                        }
                    }
                },
        )

    @Test
    fun `no submissions returns an empty list without touching the other queries`() {
        whenever(repository.fetchSubmissions(taskId, false, page)).thenReturn(emptyList())

        assertTrue(service.getReview(taskId, false, page).isEmpty())

        verify(repository, never()).fetchQuestions(any())
        verify(repository, never()).fetchFieldSources(any())
    }

    @Test
    fun `every question of the form is listed in order, including unanswered ones`() {
        whenever(repository.fetchSubmissions(taskId, false, page)).thenReturn(listOf(row(mapOf("fan_count" to 5))))
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)
        whenever(repository.fetchFieldSources(listOf(responseId))).thenReturn(emptyMap())

        val fields = service.getReview(taskId, false, page).single().fields

        assertEquals(listOf("fan_count", "note", "farm_id"), fields.map { it.fieldName })
        assertEquals("5", fields[0].value)
        assertNull(fields[1].value)
    }

    @Test
    fun `source comes from the chat answers and is null when the chatbot wasn't involved`() {
        whenever(repository.fetchSubmissions(taskId, false, page)).thenReturn(listOf(row(mapOf("fan_count" to 5, "note" to "ok"))))
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)
        whenever(repository.fetchFieldSources(listOf(responseId)))
            .thenReturn(mapOf(responseId to mapOf("fan_count" to "llm_extracted", "note" to "guided_flow")))

        val fields = service.getReview(taskId, false, page).single().fields

        assertEquals("llm_extracted", fields[0].source)
        assertEquals("guided_flow", fields[1].source)
        assertNull(fields[2].source)
    }

    @Test
    fun `a submission with no chat link at all has no sources`() {
        whenever(repository.fetchSubmissions(taskId, false, page)).thenReturn(listOf(row(mapOf("fan_count" to 5))))
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)
        whenever(repository.fetchFieldSources(listOf(responseId))).thenReturn(emptyMap())

        assertTrue(service.getReview(taskId, false, page).single().fields.all { it.source == null })
    }

    @Test
    fun `editable is true only for input types a correction can handle`() {
        whenever(repository.fetchSubmissions(taskId, false, page)).thenReturn(listOf(row(mapOf("farm_id" to "abc"))))
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)
        whenever(repository.fetchFieldSources(listOf(responseId))).thenReturn(emptyMap())

        val fields = service.getReview(taskId, false, page).single().fields

        assertTrue(fields[0].editable)
        assertTrue(fields[1].editable)
        assertFalse(fields[2].editable)
    }

    @Test
    fun `a response with an empty answer body still lists its fields with no values`() {
        whenever(repository.fetchSubmissions(taskId, false, page)).thenReturn(listOf(row(null)))
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)
        whenever(repository.fetchFieldSources(listOf(responseId))).thenReturn(emptyMap())

        assertTrue(service.getReview(taskId, false, page).single().fields.all { it.value == null })
    }

    @Test
    fun `the aiOnly flag and paging are passed straight to the query`() {
        val second = PageRequest(page = 2, size = 10)
        whenever(repository.fetchSubmissions(eq(taskId), eq(true), eq(second))).thenReturn(emptyList())

        service.getReview(taskId, true, second)

        verify(repository).fetchSubmissions(taskId, true, second)
    }
}

class ResponseReviewServiceCorrectionTest {
    private val repository: ResponseReviewRepository = mock()
    private val correctionLog: ResponseCorrectionLogRepository = mock()
    private val service = ResponseReviewService(repository, correctionLog)

    private val taskId = UUID.randomUUID()
    private val responseId = UUID.randomUUID()
    private val reviewer = UUID.randomUUID()

    private val questions =
        listOf(
            QuestionMeta("fan_count", "Number of fans", "INT"),
            QuestionMeta("farm_id", "Farm", "OPTION"),
        )

    // The transaction the real repository would hand to afterUpdate.
    private val tx: DSLContext = mock()

    // Stands in for the real locked read-modify-write: hands the stored value
    // to the lambda the service passed in, then runs afterUpdate inside "the
    // transaction" exactly as the repository does, and reports what came back.
    private fun storedValueIs(existing: JsonNode?) {
        whenever(repository.updateAnswerField(eq(taskId), eq(responseId), eq("fan_count"), any(), any())).thenAnswer { call ->
            val newValueFor = call.getArgument<(JsonNode?) -> JsonNode>(3)
            val afterUpdate = call.getArgument<(DSLContext, ResponseReviewRepository.Correction) -> Unit>(4)
            ResponseReviewRepository.Correction(existing, newValueFor(existing)).also { afterUpdate(tx, it) }
        }
    }

    @Test
    fun `a valid correction is applied and returned as the field now reads`() {
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)
        storedValueIs(IntNode.valueOf(5))
        whenever(repository.fetchFieldSources(listOf(responseId)))
            .thenReturn(mapOf(responseId to mapOf("fan_count" to "llm_extracted")))

        val field = service.correctField(taskId, responseId, "fan_count", ResponseReview.Request.Correct("7", "typo"), reviewer)

        assertEquals("fan_count", field.fieldName)
        assertEquals("7", field.value)
        assertEquals("llm_extracted", field.source)
        assertTrue(field.editable)
    }

    // US2-8 #173: every applied correction lands in the audit trail with the
    // before/after values, who made it and why.
    @Test
    fun `a valid correction is recorded in the audit log`() {
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)
        storedValueIs(IntNode.valueOf(5))
        whenever(repository.fetchFieldSources(listOf(responseId))).thenReturn(emptyMap())

        service.correctField(taskId, responseId, "fan_count", ResponseReview.Request.Correct("7", "typo"), reviewer)

        verify(correctionLog).record(tx, responseId, "fan_count", "5", "7", reviewer, "typo")
    }

    // docs-and-plan#225: the audit row is written through the answer's own
    // transaction, so a failed insert fails the whole correction (and the
    // repository's transactionResult rolls the UPDATE back) instead of
    // leaving a changed answer with no record of the change.
    @Test
    fun `a failed audit write fails the correction instead of being skipped`() {
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)
        storedValueIs(IntNode.valueOf(5))
        doThrow(IllegalStateException("insert failed"))
            .whenever(correctionLog)
            .record(any(), any(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull())

        assertThrows(IllegalStateException::class.java) {
            service.correctField(taskId, responseId, "fan_count", ResponseReview.Request.Correct("7"), reviewer)
        }
        verify(repository, never()).fetchFieldSources(any())
    }

    @Test
    fun `nothing is recorded in the audit log when the correction is rejected`() {
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)

        assertThrows(IllegalArgumentException::class.java) {
            service.correctField(taskId, responseId, "farm_id", ResponseReview.Request.Correct("abc"), reviewer)
        }
        verify(correctionLog, never()).record(any(), any(), any(), anyOrNull(), anyOrNull(), any(), anyOrNull())
    }

    @Test
    fun `an unknown field is rejected before anything is written`() {
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)

        assertThrows(IllegalArgumentException::class.java) {
            service.correctField(taskId, responseId, "nope", ResponseReview.Request.Correct("7"), reviewer)
        }
        verify(repository, never()).updateAnswerField(any(), any(), any(), any(), any())
    }

    @Test
    fun `a field type that cannot be corrected is rejected before anything is written`() {
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)

        assertThrows(IllegalArgumentException::class.java) {
            service.correctField(taskId, responseId, "farm_id", ResponseReview.Request.Correct("abc"), reviewer)
        }
        verify(repository, never()).updateAnswerField(any(), any(), any(), any(), any())
    }

    @Test
    fun `a value that is wrong for the field type is rejected`() {
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)
        storedValueIs(IntNode.valueOf(5))

        assertThrows(IllegalArgumentException::class.java) {
            service.correctField(taskId, responseId, "fan_count", ResponseReview.Request.Correct("ห้า"), reviewer)
        }
    }

    @Test
    fun `a response that does not exist under the task is a not-found`() {
        whenever(repository.fetchQuestions(taskId)).thenReturn(questions)
        whenever(repository.updateAnswerField(any(), any(), any(), any(), any())).thenReturn(null)

        assertThrows(EntityNotFoundException::class.java) {
            service.correctField(taskId, responseId, "fan_count", ResponseReview.Request.Correct("7"), reviewer)
        }
    }
}
