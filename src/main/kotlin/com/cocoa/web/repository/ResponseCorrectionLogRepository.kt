package com.cocoa.web.repository

import com.cocoa.web.base.BaseRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.util.UUID

// US2-8 (docs-and-plan#173): the persistent audit trail of researcher
// corrections. ResponseReviewService appends one row here on every successful
// correctField, so nothing is silently overwritten.
//
// Written with name-based SQL rather than generated jOOQ tables -- the same
// approach ResponseReviewRepository uses for chat.* -- so the build does not
// depend on the V29 table already existing in the jOOQ codegen database. Backs
// database migration V29__response_correction_log.sql; correction_log_id and
// corrected_at use their column defaults.
@Repository
class ResponseCorrectionLogRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    // `tx` is the transaction that just changed the answer
    // (ResponseReviewRepository.updateAnswerField's afterUpdate), so the
    // correction and its audit row commit or roll back together
    // (docs-and-plan#225). Never written through this repository's own dsl.
    fun record(
        tx: DSLContext,
        responseId: UUID,
        fieldName: String,
        oldValue: String?,
        newValue: String?,
        correctedBy: UUID,
        reason: String?,
    ) {
        tx.insertInto(DSL.table(DSL.name("form", "response_correction_log")))
            .columns(
                DSL.field(DSL.name("response_id"), UUID::class.java),
                DSL.field(DSL.name("field_name"), String::class.java),
                DSL.field(DSL.name("old_value"), String::class.java),
                DSL.field(DSL.name("new_value"), String::class.java),
                DSL.field(DSL.name("corrected_by"), UUID::class.java),
                DSL.field(DSL.name("reason"), String::class.java),
            )
            .values(responseId, fieldName, oldValue, newValue, correctedBy, reason)
            .execute()
    }
}
