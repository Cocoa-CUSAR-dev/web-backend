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

        data class Create(
            val title: String,
            val description: String?,
            val sortOrder: Int,
            val questions: List<Question.Request.Create>,
        )
         
        // sectionId == null means "create this section"; otherwise it must
        // name a section that already belongs to the form being updated.
        data class Update(
            val sectionId: UUID?,
            val title: String,
            val description: String?,
            val isActive: Boolean = true,
            val sortOrder: Int,
            val questions: List<Question.Request.Update>,
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
