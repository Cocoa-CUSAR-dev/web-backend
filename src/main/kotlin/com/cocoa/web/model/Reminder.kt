package com.cocoa.web.model

import java.time.LocalTime

object Reminder {
    object Request {
        /**
         * Deliberately has no `cadence` field. The chatbot service's own
         * reminder job only ever recognizes a "DAILY" schedule (see its
         * due_reminders query) -- any other value would be a row the job
         * silently never picks up, with no error anywhere to surface that.
         * ChatbotClient always sends "DAILY" itself; this type just never
         * gives a caller the option to send anything else.
         */
        data class Create(
            val enabled: Boolean,
            val timeOfDay: LocalTime,
        )
    }
}
