package com.cocoa.web.repository

import com.cocoa.generated.auth.Tables.SSO_USED_TOKEN
import com.cocoa.web.base.BaseRepository
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.UUID

// US3-2 (docs-and-plan#125, F3): records each redeemed SSO token's jti so
// /auth/sso/exchange can refuse a replay. Backs auth.sso_used_token (database
// repo V27__sso_single_use_token.sql).
@Repository
class SsoUsedTokenRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    // Atomic claim via INSERT ... ON CONFLICT DO NOTHING: execute() returns the
    // number of rows inserted. 1 => this caller is the first (and only) one to
    // redeem the jti; 0 => it was already used. Doing the check and the mark in
    // one statement means two concurrent exchanges of the same token can't both
    // win -- exactly one insert succeeds.
    fun markUsedIfFirstTime(
        jti: UUID,
        expiresAt: LocalDateTime,
    ): Boolean {
        val inserted =
            dsl.insertInto(SSO_USED_TOKEN)
                .set(SSO_USED_TOKEN.JTI, jti)
                .set(SSO_USED_TOKEN.EXPIRES_AT, expiresAt)
                .onConflictDoNothing()
                .execute()
        return inserted > 0
    }
}
