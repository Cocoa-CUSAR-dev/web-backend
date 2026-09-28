package com.cocoa.web.model

import java.time.LocalTime
import java.util.UUID

object Reminder {
    /** ROLE = everyone holding that role (id is a role_id); USER = one person (id is a user_id). */
    enum class RecipientType { ROLE, USER }

    data class Recipient(
        val type: RecipientType,
        val id: UUID,
    )

    /** A Recipient plus a human-readable name (role name / username) for the UI. */
    data class RecipientDetail(
        val type: RecipientType,
        val id: UUID,
        val label: String,
    )

    object Request {
        /**
         * Deliberately has no `cadence` field. The chatbot service's own
         * reminder job only ever recognizes a "DAILY" schedule (see its
         * due_reminders query) -- any other value would be a row the job
         * silently never picks up, with no error anywhere to surface that.
         * The chatbot always creates "DAILY" itself; this type just never
         * gives a caller the option to send anything else.
         *
         * Used both inside Form.Request.Create.reminder and as the body of
         * PUT /forms/{formId}/reminder. No recipients = remind everyone who
         * still owes the task.
         */
        data class Create(
            val enabled: Boolean,
            val timeOfDay: LocalTime,
            val recipients: List<Recipient> = emptyList(),
        )
    }

    /** What the reminder settings screen shows for one form's task. */
    data class Detail(
        val scheduleId: UUID?,
        val enabled: Boolean,
        val timeOfDay: LocalTime?,
        val recipients: List<RecipientDetail>,
    )

    data class RoleOption(
        val roleId: UUID,
        val roleName: String,
    )

    data class UserOption(
        val userId: UUID,
        val username: String,
        val roles: List<String>,
    )
}
