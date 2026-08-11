package com.cocoa.web.model

import java.util.UUID

object Section {
    data class Entity(
        val sectionId: UUID,
        val formId: UUID,
        val title: String,
        val description: String?,
        val isActive: Boolean,
        val sortOrder: Int,
    )

    data class Detail(
        val sectionId: UUID,
        val title: String,
        val description: String?,
        val sortOrder: Int,
        val isActive: Boolean,
        val questions: List<Question.Entity>,
    )

    object Request {
        data class Edit(
            val sectionId: UUID,
            val description: String?,
            val isActive: Boolean,
            val questions: List<Question.Request.Edit>,
        )
    }

    fun Entity.toDetail(questions: List<Question.Entity>): Detail {
        return Detail(
            sectionId = this.sectionId,
            title = this.title,
            description = this.description,
            sortOrder = this.sortOrder,
            isActive = this.isActive,
            questions = questions,
        )
    }
}
