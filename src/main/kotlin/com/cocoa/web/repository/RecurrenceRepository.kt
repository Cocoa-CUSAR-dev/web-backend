package com.cocoa.web.repository

import com.cocoa.generated.auth.Tables.ROLE
import com.cocoa.generated.auth.Tables.USER_ACCOUNT
import com.cocoa.generated.auth.Tables.USER_ROLE
import com.cocoa.generated.form.Tables.QUESTION
import com.cocoa.generated.form.Tables.QUESTION_VISIBILITY
import com.cocoa.generated.form.Tables.SECTION
import com.cocoa.generated.form.Tables.TASK
import com.cocoa.generated.form.Tables.TASK_FORM
import com.cocoa.generated.form.Tables.TASK_RECIPIENT
import com.cocoa.generated.form.Tables.TASK_RECURRENCE
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.model.Recurrence
import com.cocoa.web.model.Reminder
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Repository
class RecurrenceRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    /**
     * Recurrences the job still has to look at: switched on, and either
     * open-ended or ending no earlier than [windowStart] (the oldest day the
     * job would still open a round for).
     */
    fun fetchRunning(windowStart: LocalDate): List<Recurrence.Active> =
        dsl.select(
            TASK_RECURRENCE.RECURRENCE_ID,
            TASK_RECURRENCE.TEMPLATE_TASK_ID,
            TASK_RECURRENCE.FREQUENCY,
            TASK_RECURRENCE.WEEKDAYS,
            TASK_RECURRENCE.START_DATE,
            TASK_RECURRENCE.UNTIL_DATE,
            TASK_RECURRENCE.CREATED_BY,
            TASK.OPEN_AT,
            TASK.CLOSE_AT,
        )
            .from(TASK_RECURRENCE)
            .join(TASK).on(TASK.TASK_ID.eq(TASK_RECURRENCE.TEMPLATE_TASK_ID))
            .where(TASK_RECURRENCE.IS_ACTIVE.isTrue)
            .and(TASK_RECURRENCE.UNTIL_DATE.isNull.or(TASK_RECURRENCE.UNTIL_DATE.ge(windowStart)))
            .orderBy(TASK_RECURRENCE.CREATED_AT, TASK_RECURRENCE.RECURRENCE_ID)
            .fetch { record ->
                Recurrence.Active(
                    recurrenceId = record.get(TASK_RECURRENCE.RECURRENCE_ID),
                    templateTaskId = record.get(TASK_RECURRENCE.TEMPLATE_TASK_ID),
                    frequency = Recurrence.Frequency.valueOf(record.get(TASK_RECURRENCE.FREQUENCY)),
                    weekdays = record.get(TASK_RECURRENCE.WEEKDAYS)?.map { it.toInt() } ?: emptyList(),
                    startDate = record.get(TASK_RECURRENCE.START_DATE),
                    untilDate = record.get(TASK_RECURRENCE.UNTIL_DATE),
                    createdBy = record.get(TASK_RECURRENCE.CREATED_BY),
                    templateOpenAt = record.get(TASK.OPEN_AT),
                    templateCloseAt = record.get(TASK.CLOSE_AT),
                )
            }

    /** Which of [dates] already have a round -- lets the job skip them without a write. */
    fun fetchExistingPeriods(
        recurrenceId: UUID,
        dates: List<LocalDate>,
    ): Set<LocalDate> =
        dsl.select(TASK.PERIOD_DATE).from(TASK)
            .where(TASK.RECURRENCE_ID.eq(recurrenceId))
            .and(TASK.PERIOD_DATE.`in`(dates))
            .fetch(TASK.PERIOD_DATE)
            .toSet()

    /** The ROLE / USER rules a task is aimed at, in the order they were added. */
    fun fetchRecipients(taskId: UUID): List<Reminder.Recipient> =
        dsl.selectFrom(TASK_RECIPIENT)
            .where(TASK_RECIPIENT.TASK_ID.eq(taskId))
            .orderBy(TASK_RECIPIENT.CREATED_AT, TASK_RECIPIENT.RECIPIENT_ID)
            .fetch { record ->
                val type = Reminder.RecipientType.valueOf(record.recipientType)
                Reminder.Recipient(
                    type = type,
                    id = if (type == Reminder.RecipientType.ROLE) record.roleId else record.userId,
                )
            }

    /** Recipients whose role or user does not exist -- a typo or a stale id from the form. */
    fun findUnknownRecipients(recipients: List<Reminder.Recipient>): List<Reminder.Recipient> {
        val roleIds = recipients.filter { it.type == Reminder.RecipientType.ROLE }.map { it.id }.distinct()
        val userIds = recipients.filter { it.type == Reminder.RecipientType.USER }.map { it.id }.distinct()

        val knownRoles =
            if (roleIds.isEmpty()) {
                emptySet()
            } else {
                dsl.select(ROLE.ROLE_ID).from(ROLE).where(ROLE.ROLE_ID.`in`(roleIds)).fetch(ROLE.ROLE_ID).toSet()
            }
        val knownUsers =
            if (userIds.isEmpty()) {
                emptySet()
            } else {
                dsl.select(USER_ACCOUNT.USER_ID).from(USER_ACCOUNT)
                    .where(USER_ACCOUNT.USER_ID.`in`(userIds)).fetch(USER_ACCOUNT.USER_ID).toSet()
            }

        return recipients.filter {
            when (it.type) {
                Reminder.RecipientType.ROLE -> it.id !in knownRoles
                Reminder.RecipientType.USER -> it.id !in knownUsers
            }
        }
    }

    /**
     * The people the rules name right now: everyone holding a ROLE rule's role,
     * plus each USER rule's person, without duplicates. Resolved when a round is
     * created, so someone who joined the role since the last round is included.
     */
    fun resolveUserIds(recipients: List<Reminder.Recipient>): List<UUID> {
        val roleIds = recipients.filter { it.type == Reminder.RecipientType.ROLE }.map { it.id }.distinct()
        val userIds = recipients.filter { it.type == Reminder.RecipientType.USER }.map { it.id }.distinct()

        val fromRoles =
            if (roleIds.isEmpty()) {
                emptyList()
            } else {
                dsl.selectDistinct(USER_ROLE.USER_ID).from(USER_ROLE)
                    .where(USER_ROLE.ROLE_ID.`in`(roleIds)).fetch(USER_ROLE.USER_ID)
            }
        // A USER rule only counts if that account still exists.
        val named =
            if (userIds.isEmpty()) {
                emptyList()
            } else {
                dsl.select(USER_ACCOUNT.USER_ID).from(USER_ACCOUNT)
                    .where(USER_ACCOUNT.USER_ID.`in`(userIds)).fetch(USER_ACCOUNT.USER_ID)
            }

        return (fromRoles + named).distinct()
    }

    /**
     * Opens the round of [recurrence] for [periodDate]: a copy of the template
     * (task, form, sections, questions and who may see each question), with the
     * given open/close time, assigned to [assigneeIds]. Everything happens in one
     * transaction, so a failure part-way leaves no half-copied form behind.
     *
     * Returns the new task's id, or null if that day's round already exists --
     * decided by the UNIQUE (recurrence_id, period_date) index, not by anything
     * the job remembers, so two overlapping runs cannot both create it.
     */
    fun createRound(
        recurrence: Recurrence.Active,
        periodDate: LocalDate,
        openAt: LocalDateTime,
        closeAt: LocalDateTime,
        assigneeIds: List<UUID>,
    ): UUID? =
        dsl.transactionResult { config ->
            val tx = DSL.using(config)

            val templateTask =
                tx.selectFrom(TASK).where(TASK.TASK_ID.eq(recurrence.templateTaskId)).fetchOne()
                    ?: throw IllegalStateException("Template task ${recurrence.templateTaskId} no longer exists")

            // copy() clears the primary key, so the database generates a new one.
            val round = templateTask.copy()
            round.openAt = openAt
            round.closeAt = closeAt
            round.recurrenceId = recurrence.recurrenceId
            round.periodDate = periodDate
            val newTaskId =
                tx.insertInto(TASK).set(round)
                    .onConflictDoNothing()
                    .returning(TASK.TASK_ID)
                    .fetchOne()?.get(TASK.TASK_ID)
                    ?: return@transactionResult null

            cloneForm(tx, recurrence.templateTaskId, newTaskId)
            TaskAssignments.assign(tx, newTaskId, assigneeIds)

            newTaskId
        }

    private fun cloneForm(
        tx: DSLContext,
        templateTaskId: UUID,
        newTaskId: UUID,
    ) {
        val templateForm =
            tx.selectFrom(TASK_FORM).where(TASK_FORM.TASK_ID.eq(templateTaskId)).fetchOne()
                ?: throw IllegalStateException("Template task $templateTaskId has no form to copy")

        val newForm = templateForm.copy()
        newForm.taskId = newTaskId
        // Keep the database's default (now) instead of the template's creation time.
        newForm.reset(TASK_FORM.CREATED_AT)
        val newFormId =
            tx.insertInto(TASK_FORM).set(newForm)
                .returning(TASK_FORM.FORM_ID)
                .fetchOne()?.get(TASK_FORM.FORM_ID)
                ?: throw IllegalStateException("Form copy failed for task $newTaskId")

        tx.selectFrom(SECTION)
            .where(SECTION.FORM_ID.eq(templateForm.formId))
            .orderBy(SECTION.SORT_ORDER, SECTION.SECTION_ID)
            .fetch()
            .forEach { section ->
                val newSection = section.copy()
                newSection.formId = newFormId
                val newSectionId =
                    tx.insertInto(SECTION).set(newSection)
                        .returning(SECTION.SECTION_ID)
                        .fetchOne()?.get(SECTION.SECTION_ID)
                        ?: throw IllegalStateException("Section copy failed for task $newTaskId")

                tx.selectFrom(QUESTION)
                    .where(QUESTION.SECTION_ID.eq(section.sectionId))
                    .orderBy(QUESTION.SORT_ORDER, QUESTION.QUESTION_ID)
                    .fetch()
                    .forEach { question ->
                        val newQuestion = question.copy()
                        newQuestion.sectionId = newSectionId
                        val newQuestionId =
                            tx.insertInto(QUESTION).set(newQuestion)
                                .returning(QUESTION.QUESTION_ID)
                                .fetchOne()?.get(QUESTION.QUESTION_ID)
                                ?: throw IllegalStateException("Question copy failed for task $newTaskId")

                        // Who may see the question (by role / province) travels with it.
                        tx.insertInto(QUESTION_VISIBILITY)
                            .columns(
                                QUESTION_VISIBILITY.QUESTION_ID,
                                QUESTION_VISIBILITY.ROLE,
                                QUESTION_VISIBILITY.PROVINCE_ID,
                            )
                            .select(
                                DSL.select(
                                    DSL.`val`(newQuestionId),
                                    QUESTION_VISIBILITY.ROLE,
                                    QUESTION_VISIBILITY.PROVINCE_ID,
                                )
                                    .from(QUESTION_VISIBILITY)
                                    .where(QUESTION_VISIBILITY.QUESTION_ID.eq(question.questionId)),
                            )
                            .execute()
                    }
            }
    }
}
