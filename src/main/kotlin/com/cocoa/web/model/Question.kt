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
        // form.question.carry_forward (V21). On a multi-submit form, a
        // flagged question is answered for the farmer from the row they just
        // submitted instead of being asked again -- farm and plot stay put
        // while the activity changes. Lives on Entity because this is the
        // DTO sections[].questions[] carries through /service/forms/{formId},
        // which is how the chatbot learns it.
        val carryForward: Boolean,
        val sortOrder: Int,
        val choices: List<Choice>?,
        val validationRule: JsonNode?,
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
            val carryForward: Boolean = false,
            val sortOrder: Int,
            val defaultValue: JsonNode? = null,
        )

        // questionId == null means "create this question"; otherwise it
        // must name a question that already belongs to the section being
        // updated.
        data class Update(
            val questionId: UUID?,
            val label: String,
            val description: String?,
            val inputType: String,
            val fieldName: String?,
            val isMandatory: Boolean = false,
            val isActive: Boolean = true,
            // Nullable-means-unchanged, unlike the write-always fields around
            // it -- same reasoning as Form.Request.Update.isMultipleSubmit: a
            // client that doesn't know this field yet (every client until the
            // web-app checkbox ships) must not silently clear a flag a
            // researcher set. New questions treat null as false.
            val carryForward: Boolean? = null,
            val sortOrder: Int,
            val defaultValue: JsonNode? = null,
        )
    }
}
