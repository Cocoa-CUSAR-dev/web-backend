package com.cocoa.web.model

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID

object Question {
    data class Entity(
        val questionId: UUID,
        val sectionId: UUID,
        val label: String,
        val inputType: String,
        val description: String?,
        val fieldName: String?,
        val defaultValue: JsonNode?,
        val isMandatory: Boolean,
        val isActive: Boolean,
        val sortOrder: Int,
        val choices: List<Choice>?,
    )

    data class Choice(
        val id: String,
        val name: String,
    )

    object Request {
        data class Edit(
            val questionId: UUID,
            val description: String?,
            val isActive: Boolean?,
            val isMandatory: Boolean?,
        )

        data class Create(
            val label: String,
            val description: String?,
            val inputType: String,
            val fieldName: String?,
            val isMandatory: Boolean = false,
            val sortOrder: Int,
            val defaultValue: JsonNode? = null,
        )
    }
}
