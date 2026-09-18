package com.cocoa.web.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// US2-6 (docs-and-plan#130): daily diary generated from a farmer's own
// submitted answers -- see form.diary_entry (database repo, V22).
object Diary {
    data class Entity(
        val userId: UUID,
        val entryDate: LocalDate,
        val diaryText: String,
        val updatedAt: LocalDateTime,
    )

    // One fact pulled out of a form.response row for a given day, before
    // reference-label resolution. rawValue is whatever the farmer's answer
    // stored for this field (could be a UUID for OPTION/reference fields,
    // "true"/"false" for BOOLEAN, or plain text/number otherwise).
    data class AnswerField(
        val fieldName: String,
        val label: String,
        val inputType: String,
        val rawValue: String,
    )

    // Same fact, after resolving OPTION/BOOLEAN raw values into something a
    // farmer can actually read -- this is what the template renders and the
    // fact-guard checks the LLM's output against. fieldName survives
    // resolution (not just label/value) so the template can special-case
    // fields like plot_id, whose ABSENCE (not just its value) changes the
    // sentence (#135: an unanswered plot_id means "the whole farm").
    data class ResolvedField(
        val fieldName: String,
        val label: String,
        val value: String,
    )

    object Request {
        // US2-6 sub-issue #133: the chatbot has no farmer JWT to forward
        // (see ServiceKeyFilter's own comment on this class of route), so
        // it names the target farmer explicitly -- trusted only after the
        // ownership check in DiaryService.generateAndPersist.
        data class Generate(
            val userId: UUID,
        )
    }
}
