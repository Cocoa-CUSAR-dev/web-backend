package com.cocoa.web.repository

import com.cocoa.generated.form.Tables.ASSIGNMENT
import com.cocoa.generated.form.Tables.TASK_RECIPIENT
import com.cocoa.web.model.Reminder
import org.jooq.DSLContext
import java.util.UUID

/**
 * The two writes both ways of creating a targeted task share -- a researcher
 * creating one (FormRepository.createForm) and the job opening a new round of
 * a repeating one (RecurrenceRepository.createRound). Both run them inside the
 * caller's own transaction, hence the DSLContext parameter.
 */
internal object TaskAssignments {
    // Two bind parameters per row; Postgres allows 32767 per statement.
    private const val ASSIGN_CHUNK = 5000

    /** Remembers who a task is aimed at (the rules, not the people they resolve to). */
    fun saveRecipients(
        tx: DSLContext,
        taskId: UUID,
        recipients: List<Reminder.Recipient>,
    ) {
        recipients.distinct().forEach { recipient ->
            val roleId: UUID? = recipient.id.takeIf { recipient.type == Reminder.RecipientType.ROLE }
            val userId: UUID? = recipient.id.takeIf { recipient.type == Reminder.RecipientType.USER }
            tx.insertInto(TASK_RECIPIENT)
                .set(TASK_RECIPIENT.TASK_ID, taskId)
                .set(TASK_RECIPIENT.RECIPIENT_TYPE, recipient.type.name)
                .set(TASK_RECIPIENT.ROLE_ID, roleId)
                .set(TASK_RECIPIENT.USER_ID, userId)
                .execute()
        }
    }

    /**
     * One form.assignment row per person. A person already assigned to the task
     * is left alone (ON CONFLICT DO NOTHING against uq_assignment_task_user), so
     * running this twice is harmless.
     */
    fun assign(
        tx: DSLContext,
        taskId: UUID,
        userIds: List<UUID>,
    ) {
        userIds.distinct().chunked(ASSIGN_CHUNK).forEach { chunk ->
            var insert = tx.insertInto(ASSIGNMENT).columns(ASSIGNMENT.TASK_ID, ASSIGNMENT.USER_ID)
            chunk.forEach { insert = insert.values(taskId, it) }
            insert.onConflictDoNothing().execute()
        }
    }
}
