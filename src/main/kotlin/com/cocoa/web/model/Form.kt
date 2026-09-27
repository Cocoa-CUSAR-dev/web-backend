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
        // Carried on Detail (not just Entity) because /service/forms/{formId}
        // returns Detail, and that is the ONLY way the chatbot and Go can
        // learn this flag -- neither reads form.task_form directly (ADR 0001).
        // Without it here, is_multiple_submit is invisible to every consumer
        // no matter what a researcher sets. See the multi-submit design doc.
        val isMultipleSubmit: Boolean,
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
            // Defaults false so every existing caller (and the web-app until
            // its checkbox lands) keeps the single-submission behaviour it
            // has today -- opting in is explicit.
            val isMultipleSubmit: Boolean = false,
            val sections: List<Section.Request.Create>,
        )

        // Unlike Edit (which only toggles is_active/is_mandatory on rows
        // that already exist), Update writes every editable field and
        // supports adding/removing sections and questions: a section or
        // question with a null id is inserted as new, and any existing row
        // not present in `sections` is deleted. See PUT /forms/{formId}.
        data class Update(
            val description: String?,
            // Deliberately nullable-with-null-means-unchanged, unlike every
            // other field on Update (which is write-always, so omitting it
            // clears it). A researcher who turns multi-submit ON and later
            // edits only the description must not have it silently switched
            // back OFF by a client that doesn't know the field yet -- which
            // is every client until the web-app checkbox ships.
            val isMultipleSubmit: Boolean? = null,
            val sections: List<Section.Request.Update>,
        )
    }

    fun Entity.toDetail(sections: List<Section.Detail>): Detail {
        return Detail(
            formId = this.formId,
            title = this.title,
            description = this.description,
            isMultipleSubmit = this.isMultipleSubmit,
            sections = sections,
        )
    }
}
