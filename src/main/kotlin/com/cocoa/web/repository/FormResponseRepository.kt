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
import com.cocoa.web.model.Diary
import com.cocoa.web.model.FormResponse
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL.concat
import org.jooq.impl.DSL.inline
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@Repository
class FormResponseRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    companion object {
        // form.response.submitted_at is `timestamp without time zone`,
        // written as UTC wall-clock by every backend that inserts into it
        // (confirmed live 2026-09-18: mobile-backend's Docker container
        // clock is UTC, independent of whatever timezone this JVM's host
        // happens to be in). "Today"/"which day" needs to mean the
        // farmer's own calendar day, Asia/Bangkok, not the ambient JVM
        // default -- a submission at 00:17 Bangkok time is already the
        // next day for the farmer even though it's still UTC-yesterday.
        internal val FARMER_ZONE: ZoneId = ZoneId.of("Asia/Bangkok")

        internal fun LocalDateTime.toFarmerLocalDate(): LocalDate =
            this.atZone(ZoneOffset.UTC).withZoneSameInstant(FARMER_ZONE).toLocalDate()

        internal fun LocalDate.farmerDayRangeInUtc(): Pair<LocalDateTime, LocalDateTime> {
            val start = this.atStartOfDay(FARMER_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
            val end = this.plusDays(1).atStartOfDay(FARMER_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
            return start to end
        }
    }

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

    // US2-6/US5-1 (docs-and-plan#130): every calendar day this farmer has
    // submitted at least one response on, newest first -- backs the
    // submission-history list page (web-app's fallback + entry point into a
    // single day's detail).
    fun fetchOwnSubmissionDays(userId: UUID): List<LocalDate> {
        return dsl.selectDistinct(RESPONSE.SUBMITTED_AT)
            .from(RESPONSE)
            .where(RESPONSE.USER_ID.eq(userId))
            .and(RESPONSE.SUBMITTED_AT.isNotNull)
            .fetch(RESPONSE.SUBMITTED_AT)
            .map { it.toFarmerLocalDate() }
            .distinct()
            .sortedDescending()
    }

    // US2-6 (docs-and-plan#130): every answered field this farmer submitted
    // on `date`, across every task -- the raw input to both the fallback
    // "table of forms" view (rendered as-is) and diary generation (rendered
    // through the reference-label resolver + template + LLM polish first).
    // One query serves both call sites so there's a single source of truth
    // for "what did this farmer actually submit that day."
    //
    // Joins on TASK_FORM_ID, not TASK_LOG_ID like fetchTaskResponses/
    // fetchUserResponses above -- DB-1 already documents task_log_id as
    // actually holding task.task_id, not a form_id, so a join through it
    // here would silently find nothing. task_form_id (V9) is the real FK to
    // form.task_form.form_id; mobile-backend now populates it on submit
    // (still null on rows submitted before that fix, which is fine -- see
    // form.diary_entry's own "don't backfill old days" comment).
    fun fetchAnswerFieldsForUserAndDate(
        userId: UUID,
        date: LocalDate,
    ): List<Diary.AnswerField> {
        val (dayStart, dayEnd) = date.farmerDayRangeInUtc()

        val responses =
            dsl.select(RESPONSE.TASK_FORM_ID, RESPONSE.ANSWER)
                .from(RESPONSE)
                .where(RESPONSE.USER_ID.eq(userId))
                .and(RESPONSE.SUBMITTED_AT.ge(dayStart))
                .and(RESPONSE.SUBMITTED_AT.lt(dayEnd))
                .fetch()

        if (responses.isEmpty()) return emptyList()

        val taskFormIds = responses.mapNotNull { it.get(RESPONSE.TASK_FORM_ID) }.distinct()
        if (taskFormIds.isEmpty()) return emptyList()

        // Keyed by (form_id, field_name): the same field_name can carry a
        // different label/input_type on a different form, so the lookup
        // must stay scoped per-form rather than assuming a global meaning
        // the way form.field_validation_rule (V16) is allowed to.
        val questionMeta =
            dsl.select(SECTION.FORM_ID, QUESTION.FIELD_NAME, QUESTION.LABEL, QUESTION.INPUT_TYPE)
                .from(QUESTION)
                .join(SECTION).on(SECTION.SECTION_ID.eq(QUESTION.SECTION_ID))
                .where(SECTION.FORM_ID.`in`(taskFormIds))
                .and(QUESTION.FIELD_NAME.isNotNull)
                .fetch()
                .associateBy { it.get(SECTION.FORM_ID) to it.get(QUESTION.FIELD_NAME) }

        return responses.flatMap { row ->
            val formId = row.get(RESPONSE.TASK_FORM_ID)
            val answer = row.get(RESPONSE.ANSWER)
            if (formId == null || answer == null) return@flatMap emptyList()

            answer.properties().asSequence().mapNotNull { (fieldName, valueNode) ->
                if (valueNode.isNull) return@mapNotNull null
                val meta = questionMeta[formId to fieldName] ?: return@mapNotNull null
                val value = valueNode.asText().takeIf { it.isNotBlank() } ?: return@mapNotNull null

                Diary.AnswerField(
                    fieldName = fieldName,
                    label = meta.get(QUESTION.LABEL) ?: fieldName,
                    inputType = meta.get(QUESTION.INPUT_TYPE) ?: "",
                    rawValue = value,
                )
            }.toList()
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
