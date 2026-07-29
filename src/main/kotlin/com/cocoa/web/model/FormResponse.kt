package com.cocoa.web.model

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime
import java.util.UUID

object FormResponse {
    data class Entity(
        val responseId: UUID,
        val formId: UUID,
        val userId: UUID,
        val submittedAt: LocalDateTime,
        val answer: JsonNode,
        val status: String,
    )

    data class Detail(
        val questionTitle: String,
        val answers: List<Answer>,
    )

    data class Answer(
        val fullName: String,
        val answer: String,
    )
}
