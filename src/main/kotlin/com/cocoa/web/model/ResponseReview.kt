package com.cocoa.web.model

import java.time.LocalDateTime
import java.util.UUID

// US2-8 (docs-and-plan#171/#172): what a researcher sees when reviewing the
// answers farmers submitted through the chatbot, and what they send to fix
// one.
object ResponseReview {
    /**
     * One question's answer inside a submission.
     *
     * [source] is where the chatbot got the value: "llm_extracted" (an AI
     * pulled it out of free text), "guided_flow" (the farmer answered a
     * direct question), or null when the submission didn't come through the
     * chatbot at all (e.g. the mobile app) so there is nothing to say.
     * [editable] is decided here, once, so the web app doesn't have to
     * duplicate the list of input types a correction can handle.
     */
    data class Field(
        val fieldName: String,
        val label: String,
        val inputType: String,
        val value: String?,
        val source: String?,
        val editable: Boolean,
    )

    data class Submission(
        val responseId: UUID,
        val submitter: String,
        val submittedAt: LocalDateTime?,
        val fields: List<Field>,
    )

    object Request {
        /** [reason] is optional free text for whoever audits corrections later. */
        data class Correct(
            val value: String,
            val reason: String? = null,
        )
    }
}
