package com.cocoa.web.service

import com.cocoa.web.base.PageRequest
import com.cocoa.web.repository.ResponseReviewRepository
import com.cocoa.web.repository.ResponseReviewRepository.QuestionMeta
import com.cocoa.web.repository.ResponseReviewRepository.SubmissionRow
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.UUID

class ResponseReviewServiceTest {
    private val repository: ResponseReviewRepository = mock()
    private val service = ResponseReviewService(repository)

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
