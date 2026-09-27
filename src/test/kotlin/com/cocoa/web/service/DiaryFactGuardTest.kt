package com.cocoa.web.service

import com.cocoa.web.model.Diary
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiaryFactGuardTest {
    private val facts =
        listOf(
            Diary.ResolvedField("fertilizer_id", "ปุ๋ย", "สูตร 15-15-15"),
            Diary.ResolvedField("amount", "ปริมาณ", "2 กิโลกรัม"),
        )

    @Test
    fun `passes when every fact's value survives rephrasing`() {
        val polished = "วันนี้คุณใส่ปุ๋ยสูตร 15-15-15 จำนวน 2 กิโลกรัม"

        assertTrue(DiaryFactGuard.preservesFacts(facts, polished))
    }

    @Test
    fun `fails when the LLM changes a quantity -- the exact data-damage risk #132 calls out`() {
        val polished = "วันนี้คุณใส่ปุ๋ยสูตร 15-15-15 จำนวน 5 กิโลกรัม"

        assertFalse(DiaryFactGuard.preservesFacts(facts, polished))
    }

    @Test
    fun `fails when the LLM drops a fact entirely`() {
        val polished = "วันนี้คุณใส่ปุ๋ยสูตร 15-15-15"

        assertFalse(DiaryFactGuard.preservesFacts(facts, polished))
    }

    @Test
    fun `blank output never passes`() {
        assertFalse(DiaryFactGuard.preservesFacts(facts, ""))
    }

    @Test
    fun `no facts to preserve trivially passes`() {
        assertTrue(DiaryFactGuard.preservesFacts(emptyList(), "anything"))
    }

    @Test
    fun `a BOOLEAN ใช่ absorbed into descriptive prose still passes -- confirmed live 2026-09-17`() {
        val withBoolean =
            facts + Diary.ResolvedField("is_quality_damage", "ทำให้ผลโกโก้เสียคุณภาพหรือไม่", "ใช่")
        val polished =
            "วันนี้คุณใส่ปุ๋ยสูตร 15-15-15 จำนวน 2 กิโลกรัม ซึ่งทำให้ผลโกโก้เสียคุณภาพ"

        assertTrue(DiaryFactGuard.preservesFacts(withBoolean, polished))
    }

    @Test
    fun `a BOOLEAN ไม่ absorbed into descriptive prose still passes`() {
        val withBoolean =
            facts + Diary.ResolvedField("is_quality_damage", "ทำให้ผลโกโก้เสียคุณภาพหรือไม่", "ไม่")
        val polished =
            "วันนี้คุณใส่ปุ๋ยสูตร 15-15-15 จำนวน 2 กิโลกรัม โดยไม่พบความเสียหายต่อผลผลิตแต่อย่างใด"

        assertTrue(DiaryFactGuard.preservesFacts(withBoolean, polished))
    }
}
