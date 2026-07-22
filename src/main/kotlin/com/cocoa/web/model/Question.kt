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
        val choices: List<String>?
    )

    object Request {
        data class Edit(
            val questionId: UUID,
            val description: String?,
            val isActive: Boolean?,
            val isMandatory: Boolean?,
        )
    }

}
