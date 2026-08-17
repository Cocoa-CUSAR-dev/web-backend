package com.cocoa.web.model

import java.time.LocalDateTime
import java.util.UUID

object Form {
    data class Entity(
        val formId: UUID,
        val taskId: UUID,
        val title: String,
        val content: String,
        val handler: String,
        val isMultipleSubmit: Boolean,
        val description: String?,
        val createdAt: LocalDateTime,
    )

    data class Detail(
        val formId: UUID,
        val title: String,
        val description: String?,
        val sections: List<Section.Detail>,
    )

    object Request {
        data class Edit(
            val description: String?,
            val sections: List<Section.Request.Edit>,
        )

        data class Create(
            val title: String,
            val description: String?,
            val taskType: String = "FORM",
            val openAt: LocalDateTime,
            val closeAt: LocalDateTime,
            val handler: String,
            val sections: List<Section.Request.Create>,
        )

        // Unlike Edit (which only toggles is_active/is_mandatory on rows
        // that already exist), Update writes every editable field and
        // supports adding/removing sections and questions: a section or
        // question with a null id is inserted as new, and any existing row
        // not present in `sections` is deleted. See PUT /forms/{formId}.
        data class Update(
            val description: String?,
            val sections: List<Section.Request.Update>,
        )
    }

    fun Entity.toDetail(sections: List<Section.Detail>): Detail {
        return Detail(
            formId = this.formId,
            title = this.title,
            description = this.description,
            sections = sections,
        )
    }
}
