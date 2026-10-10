package com.cocoa.web.repository

import com.cocoa.generated.agriculture.Tables.FARMER
import com.cocoa.generated.auth.Tables.USER_ACCOUNT
import com.cocoa.generated.form.Tables.QUESTION
import com.cocoa.generated.form.Tables.RESPONSE
import com.cocoa.generated.form.Tables.SECTION
import com.cocoa.generated.form.Tables.TASK_FORM
import com.cocoa.generated.processing.Tables.PROCESSOR
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.base.PageRequest
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.coalesce
import org.jooq.impl.DSL.concat
import org.jooq.impl.DSL.inline
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

// US2-8 (docs-and-plan#171/#172): data access for the researcher's
// review-and-correct screen.
//
// The chat.* tables are queried with plain SQL on purpose: jOOQ codegen
// (build.gradle.kts) only covers the schemas web-backend owns, and widening
// it would also mean widening ci-schema.sql for a read-only join. The chatbot
// remains the only writer of chat.* (ADR 0005); this only reads.
@Repository
class ResponseReviewRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    data class SubmissionRow(
        val responseId: UUID,
        val submitter: String,
        val submittedAt: LocalDateTime?,
        val answer: JsonNode?,
    )

    data class QuestionMeta(
        val fieldName: String,
        val label: String,
        val inputType: String,
    )

    data class Correction(
        val oldValue: JsonNode?,
        val newValue: JsonNode,
    )

    // task_log_id holds task.task_id, despite the name (DB-1) -- same join
    // key fetchUserResponses already relies on.
    //
    // aiOnly is a coarse, per-farmer filter ("this farmer has at least one
    // AI-extracted answer for this task"), not per-response: a farmer who
    // submitted twice, once by hand and once through the AI, keeps both
    // rows, and each field's own badge (fetchFieldSources) says which is
    // which.
    fun fetchSubmissions(
        taskId: UUID,
        aiOnly: Boolean,
        pageRequest: PageRequest,
    ): List<SubmissionRow> {
        var condition = RESPONSE.TASK_LOG_ID.eq(taskId)
        if (aiOnly) {
            condition =
                condition.and(
                    DSL.condition(
                        "exists (select 1 from chat.conversation c " +
                            "join chat.conversation_answer a on a.conversation_id = c.conversation_id " +
                            "where c.user_id = {0} and c.task_id = {1} and a.source = 'llm_extracted')",
                        RESPONSE.USER_ID,
                        RESPONSE.TASK_LOG_ID,
                    ),
                )
        }

        return dsl.select(
            RESPONSE.RESPONSE_ID,
            RESPONSE.SUBMITTED_AT,
            RESPONSE.ANSWER,
            coalesce(
                concat(FARMER.FIRST_NAME, inline(" "), FARMER.LAST_NAME),
                concat(PROCESSOR.FIRST_NAME, inline(" "), PROCESSOR.LAST_NAME),
                USER_ACCOUNT.USERNAME,
            ).`as`("submitter"),
        )
            .from(RESPONSE)
            .join(USER_ACCOUNT).on(USER_ACCOUNT.USER_ID.eq(RESPONSE.USER_ID))
            .leftJoin(FARMER).on(FARMER.USER_ID.eq(USER_ACCOUNT.USER_ID))
            .leftJoin(PROCESSOR).on(PROCESSOR.USER_ID.eq(USER_ACCOUNT.USER_ID))
            .where(condition)
            .orderBy(RESPONSE.SUBMITTED_AT.desc().nullsLast(), RESPONSE.RESPONSE_ID)
            .limit(pageRequest.size)
            .offset(pageRequest.offset)
            .fetch { row ->
                SubmissionRow(
                    responseId = row.get(RESPONSE.RESPONSE_ID),
                    submitter = row.get("submitter", String::class.java) ?: "",
                    submittedAt = row.get(RESPONSE.SUBMITTED_AT),
                    answer = row.get(RESPONSE.ANSWER),
                )
            }
    }

    // Every question of the task's form(s) in display order, so a reviewer
    // sees the same layout the farmer was asked in -- including fields the
    // farmer left empty, which is exactly where an AI is most likely to
    // have missed something.
    fun fetchQuestions(taskId: UUID): List<QuestionMeta> {
        return dsl.select(QUESTION.FIELD_NAME, QUESTION.LABEL, QUESTION.INPUT_TYPE)
            .from(QUESTION)
            .join(SECTION).on(SECTION.SECTION_ID.eq(QUESTION.SECTION_ID))
            .join(TASK_FORM).on(TASK_FORM.FORM_ID.eq(SECTION.FORM_ID))
            .where(TASK_FORM.TASK_ID.eq(taskId))
            .and(QUESTION.FIELD_NAME.isNotNull)
            .orderBy(SECTION.SORT_ORDER, QUESTION.SORT_ORDER)
            .fetch()
            .map {
                val fieldName = it.get(QUESTION.FIELD_NAME)
                QuestionMeta(
                    fieldName = fieldName,
                    label = it.get(QUESTION.LABEL) ?: fieldName,
                    inputType = it.get(QUESTION.INPUT_TYPE) ?: "VARCHAR",
                )
            }
            .distinctBy { it.fieldName }
    }

    // responseId -> (fieldName -> chat.conversation_answer.source).
    //
    // There is no foreign key from form.response to the conversation that
    // produced it, so the link is: the farmer's completed conversation for
    // the same task whose updated_at is closest to the response's
    // submitted_at. That stays right for a multi-submit task (several
    // responses and several conversations for one farmer) as long as they
    // aren't submitted within the same moment. A response with no matching
    // conversation (e.g. submitted from the mobile app) simply has no entry.
    fun fetchFieldSources(responseIds: Collection<UUID>): Map<UUID, Map<String, String>> {
        if (responseIds.isEmpty()) return emptyMap()

        val rows =
            dsl.resultQuery(
                """
                with picked as (
                    select distinct on (r.response_id) r.response_id, c.conversation_id
                    from form.response r
                    join chat.conversation c
                      on c.user_id = r.user_id
                     and c.task_id = r.task_log_id
                     and c.status = 'completed'
                    where r.response_id in ({0})
                    order by r.response_id,
                             abs(extract(epoch from (c.updated_at - r.submitted_at)))
                )
                select p.response_id, q.field_name, a.source
                from picked p
                join chat.conversation_answer a on a.conversation_id = p.conversation_id
                join form.question q on q.question_id = a.question_id
                """.trimIndent(),
                DSL.list(responseIds.map { DSL.`val`(it) }),
            ).fetch()

        return rows
            .groupBy { it.get("response_id", UUID::class.java) }
            .mapValues { (_, group) ->
                group.associate { it.get("field_name", String::class.java) to it.get("source", String::class.java) }
            }
    }

    // Rewrites one key of form.response.answer. The row is locked for the
    // read-modify-write so two researchers correcting different fields of
    // the same response can't overwrite each other with a stale copy of the
    // JSON. Returns null when the response doesn't exist under this task.
    // Every other key of the answer is left exactly as it was.
    fun updateAnswerField(
        taskId: UUID,
        responseId: UUID,
        fieldName: String,
        newValueFor: (existing: JsonNode?) -> JsonNode,
    ): Correction? {
        return dsl.transactionResult { config ->
            val tx = DSL.using(config)

            val current =
                tx.select(RESPONSE.ANSWER)
                    .from(RESPONSE)
                    .where(RESPONSE.RESPONSE_ID.eq(responseId))
                    .and(RESPONSE.TASK_LOG_ID.eq(taskId))
                    .forUpdate()
                    .fetchOne()
                    ?: return@transactionResult null

            val answer =
                current.get(RESPONSE.ANSWER) as? ObjectNode
                    ?: throw IllegalArgumentException("This response has no answer data to correct")

            val updated = answer.deepCopy()
            val oldValue = updated.get(fieldName)
            val newValue = newValueFor(oldValue)
            updated.set<JsonNode>(fieldName, newValue)

            tx.update(RESPONSE)
                .set(RESPONSE.ANSWER, updated)
                .where(RESPONSE.RESPONSE_ID.eq(responseId))
                .execute()

            Correction(oldValue, newValue)
        }
    }
}
