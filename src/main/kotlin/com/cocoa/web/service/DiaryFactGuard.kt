package com.cocoa.web.service

import com.cocoa.web.model.Diary

// US2-6 (docs-and-plan#132/#135): the actual enforcement behind "LLM เกลา
// สำนวนเท่านั้น ห้ามเติมข้อมูล" -- a prompt alone is a request, not a
// guarantee. This checks that every resolved fact's value still appears
// verbatim in the polished text.
//
// What this catches: the LLM dropping or altering a concrete value (a
// quantity, a name) -- the exact failure the sub-issue calls out ("ถ้ามีคน
// เอาไดอารี่ไปอ้างเป็นบันทึกงานวิจัย = ข้อมูลเสียหาย"). What it does NOT
// catch: the LLM padding the text with extra, unrelated sentences that
// don't contradict any known value -- detecting fabricated-but-plausible
// prose is a much harder problem than this lightweight guard attempts.
object DiaryFactGuard {
    // DiaryAnswerResolver's two BOOLEAN outputs -- confirmed live (2026-09-17)
    // that a faithful rephrasing routinely drops the literal token in favor
    // of stating the fact directly (e.g. "ทำให้ผลโกโก้เสียคุณภาพหรือไม่: ใช่"
    // became "...ซึ่งทำให้ผลโกโก้เสียคุณภาพ" -- correct, nothing invented,
    // just no substring "ใช่" left to find). Exempting them trades away
    // catching an LLM that flips ใช่/ไม่ outright; every other value type
    // (numbers, names) still needs its literal text to survive.
    private val EXEMPT_BOOLEAN_VALUES = setOf("ใช่", "ไม่")

    fun preservesFacts(
        resolvedFields: List<Diary.ResolvedField>,
        polishedText: String,
    ): Boolean {
        if (polishedText.isBlank()) return false
        return resolvedFields
            .filterNot { it.value in EXEMPT_BOOLEAN_VALUES }
            .all { polishedText.contains(it.value) }
    }
}
