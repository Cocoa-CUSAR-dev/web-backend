package com.cocoa.web.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// US4-4 (docs-and-plan#233): a task that is re-assigned automatically every
// period. The task a researcher creates is round #1 and also the template:
// each later round is a fresh copy of it, made by the job behind
// POST /service/recurrence/run.
object Recurrence {
    enum class Frequency { DAILY, WEEKLY }

    object Request {
        /**
         * How a task repeats, sent inside Form.Request.Create. The task being
         * created is the first round, so its open date is the start date.
         *
         * [weekdays] use ISO numbers (1 = Monday ... 7 = Sunday) and only mean
         * something for WEEKLY. [untilDate] is the last day a round can be for;
         * null = no end.
         */
        data class Create(
            val frequency: Frequency,
            val weekdays: List<Int>? = null,
            val untilDate: LocalDate? = null,
        )
    }

    /** A running recurrence as the round job sees it, with its template task's times. */
    data class Active(
        val recurrenceId: UUID,
        val templateTaskId: UUID,
        val frequency: Frequency,
        val weekdays: List<Int>,
        val startDate: LocalDate,
        val untilDate: LocalDate?,
        val createdBy: UUID,
        val templateOpenAt: LocalDateTime?,
        val templateCloseAt: LocalDateTime?,
    )

    /** What one run of the job did -- returned to whoever triggered it, and logged. */
    data class RunResult(
        /** New rounds opened. */
        val created: Int,
        /** The round for that day already existed (a repeat run, or another run got there first). */
        val alreadyExisted: Int,
        /** The round would already be over by now, so it was not opened. */
        val skippedClosed: Int,
        /** The recurrence has recipients but none of them resolves to a person. */
        val skippedNoRecipients: Int,
        /** Rounds that could not be created because of an error (logged). */
        val failed: Int,
    )
}
