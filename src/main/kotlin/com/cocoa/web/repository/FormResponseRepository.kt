package com.cocoa.web.repository

import com.cocoa.generated.agriculture.Tables.FARMER
import com.cocoa.generated.auth.Tables.USER_ACCOUNT
import com.cocoa.generated.form.Tables.QUESTION
import com.cocoa.generated.form.Tables.RESPONSE
import com.cocoa.generated.form.Tables.SECTION
import com.cocoa.generated.form.Tables.TASK
import com.cocoa.generated.form.Tables.TASK_FORM
import com.cocoa.generated.processing.Tables.PROCESSOR
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.base.PageRequest
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.FormResponse
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL.concat
import org.jooq.impl.DSL.inline
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class FormResponseRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    fun fetchTaskResponses(taskId: UUID): List<FormResponse.Entity> {
        val records =
            dsl.select(
                RESPONSE.RESPONSE_ID,
                RESPONSE.TASK_LOG_ID,
                RESPONSE.USER_ID,
                RESPONSE.SUBMITTED_AT,
                RESPONSE.ANSWER,
                RESPONSE.STATUS,
            )
                .from(RESPONSE)
                .join(TASK_FORM).on(TASK_FORM.FORM_ID.eq(RESPONSE.TASK_LOG_ID))
                .where(TASK_FORM.TASK_ID.eq(taskId))
                .fetch()

        return records.map { it.toFormResponseEntity() }
    }

    fun fetchTaskResponse(
        taskId: UUID,
        responseId: UUID,
    ): FormResponse.Entity? {
        val record =
            dsl.select(
                RESPONSE.RESPONSE_ID,
                RESPONSE.TASK_LOG_ID,
                RESPONSE.USER_ID,
                RESPONSE.SUBMITTED_AT,
                RESPONSE.ANSWER,
                RESPONSE.STATUS,
            )
                .from(RESPONSE)
                .join(TASK_FORM).on(TASK_FORM.FORM_ID.eq(RESPONSE.TASK_LOG_ID))
                .where(TASK_FORM.TASK_ID.eq(taskId))
                .and(RESPONSE.RESPONSE_ID.eq(responseId))
                .fetchOne()

        return record?.toFormResponseEntity()
    }

    // BE-9: this pivots per-submitter rows into one row per question, so
    // the actual unbounded fetch is `responses` below (one row per person
    // who submitted against this task) -- paginating that bounds it the
    // same way a plain list endpoint would, without changing the pivoted
    // output shape. response_id is the order key since nothing else here
    // is guaranteed unique/stable across pages.
    fun fetchUserResponses(
        taskId: UUID,
        pageRequest: PageRequest = PageRequest(),
    ): List<FormResponse.Detail> {
        val task = dsl.selectFrom(TASK).where(TASK.TASK_ID.eq(taskId)).fetchOne() ?: throw EntityNotFoundException("Task Not Found")

        val responses =
            dsl.select(
                concat(FARMER.FIRST_NAME, inline(" "), FARMER.LAST_NAME).`as`("farmer_name"),
                concat(PROCESSOR.FIRST_NAME, inline(" "), PROCESSOR.LAST_NAME).`as`("processor_name"),
                RESPONSE.ANSWER,
            )
                .from(RESPONSE)
                .join(USER_ACCOUNT).on(USER_ACCOUNT.USER_ID.eq(RESPONSE.USER_ID))
                .leftJoin(FARMER).on(USER_ACCOUNT.USER_ID.eq(FARMER.USER_ID))
                .leftJoin(PROCESSOR).on(USER_ACCOUNT.USER_ID.eq(PROCESSOR.USER_ID))
                .where(RESPONSE.TASK_LOG_ID.eq(taskId))
                .orderBy(RESPONSE.RESPONSE_ID)
                .limit(pageRequest.size)
                .offset(pageRequest.offset)
                .fetch()

        if (responses.isEmpty()) return emptyList()

        val fieldLabelMap =
            dsl.select(QUESTION.FIELD_NAME, QUESTION.LABEL)
                .from(QUESTION)
                .join(SECTION).on(SECTION.SECTION_ID.eq(QUESTION.SECTION_ID))
                .join(TASK_FORM).on(TASK_FORM.FORM_ID.eq(SECTION.FORM_ID))
                .where(TASK_FORM.TASK_ID.eq(taskId))
                .fetch()
                .associate { it.get(QUESTION.FIELD_NAME) to it.get(QUESTION.LABEL) }

        return fieldLabelMap.map { (fieldName, label) ->
            FormResponse.Detail(
                questionTitle = label ?: fieldName ?: "",
                answers =
                    responses.mapNotNull { row ->
                        val fullName =
                            row.get("farmer_name", String::class.java)
                                ?: row.get("processor_name", String::class.java)
                                ?: return@mapNotNull null

                        val value =
                            row.get(RESPONSE.ANSWER)
                                ?.get(fieldName)
                                ?.takeIf { !it.isNull }
                                ?.asText()
                                ?: return@mapNotNull null

                        FormResponse.Answer(
                            fullName = fullName,
                            answer = value,
                        )
                    },
            )
        }
    }

    // Helper Functions
    private fun Record.toFormResponseEntity(): FormResponse.Entity {
        return FormResponse.Entity(
            responseId = this.get(RESPONSE.RESPONSE_ID),
            formId = this.get(RESPONSE.TASK_LOG_ID),
            userId = this.get(RESPONSE.USER_ID),
            submittedAt = this.get(RESPONSE.SUBMITTED_AT),
            answer = this.get(RESPONSE.ANSWER),
            status = this.get(RESPONSE.STATUS),
        )
    }
}
