package com.cocoa.web.repository

import com.cocoa.generated.form.Tables.DIARY_ENTRY
import com.cocoa.web.base.BaseRepository
import com.cocoa.web.model.Diary
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// US2-6 (docs-and-plan#130): form.diary_entry (database repo, V22) -- one
// row per farmer per day, upserted whenever that day's diary is
// (re)generated.
@Repository
class DiaryRepository(
    dsl: DSLContext,
) : BaseRepository(dsl) {
    fun findByUserAndDate(
        userId: UUID,
        date: LocalDate,
    ): Diary.Entity? {
        return dsl.select(DIARY_ENTRY.USER_ID, DIARY_ENTRY.ENTRY_DATE, DIARY_ENTRY.DIARY_TEXT, DIARY_ENTRY.UPDATED_AT)
            .from(DIARY_ENTRY)
            .where(DIARY_ENTRY.USER_ID.eq(userId))
            .and(DIARY_ENTRY.ENTRY_DATE.eq(date))
            .fetchOne()
            ?.toDiaryEntity()
    }

    // Re-submitting the same day regenerates the whole day's diary rather
    // than appending -- see V22's own migration comment for why (the diary
    // summarizes everything filled that day, not just the latest
    // submission).
    fun upsert(
        userId: UUID,
        date: LocalDate,
        diaryText: String,
    ) {
        val now = LocalDateTime.now()
        dsl.insertInto(DIARY_ENTRY)
            .set(DIARY_ENTRY.USER_ID, userId)
            .set(DIARY_ENTRY.ENTRY_DATE, date)
            .set(DIARY_ENTRY.DIARY_TEXT, diaryText)
            .set(DIARY_ENTRY.CREATED_AT, now)
            .set(DIARY_ENTRY.UPDATED_AT, now)
            .onConflict(DIARY_ENTRY.USER_ID, DIARY_ENTRY.ENTRY_DATE)
            .doUpdate()
            .set(DIARY_ENTRY.DIARY_TEXT, diaryText)
            .set(DIARY_ENTRY.UPDATED_AT, now)
            .execute()
    }

    // Ownership check for the service-key-gated generate endpoint -- same
    // precedent as mobile-backend's SubmitTaskForUser (a chat.conversation
    // row existing for this user_id proves it's a real chatbot-linked
    // farmer, not an arbitrary id the caller made up; see
    // ServiceKeyFilter's own comment on why routes behind it otherwise have
    // no per-caller ownership check). chat.* is chatbot's own schema, out
    // of this service's jOOQ codegen scope (build.gradle.kts's
    // withSchemata), so this uses a raw dynamic table reference -- the same
    // technique FormRepository already uses for the ref schema.
    fun hasChatConversation(userId: UUID): Boolean {
        val userIdCol = DSL.field(DSL.name("user_id"), UUID::class.java)
        val count =
            dsl.selectCount()
                .from(DSL.table(DSL.name("chat", "conversation")))
                .where(userIdCol.eq(userId))
                .fetchOne(0, Int::class.java) ?: 0
        return count > 0
    }

    private fun Record.toDiaryEntity(): Diary.Entity {
        return Diary.Entity(
            userId = this.get(DIARY_ENTRY.USER_ID),
            entryDate = this.get(DIARY_ENTRY.ENTRY_DATE),
            diaryText = this.get(DIARY_ENTRY.DIARY_TEXT),
            updatedAt = this.get(DIARY_ENTRY.UPDATED_AT),
        )
    }
}
