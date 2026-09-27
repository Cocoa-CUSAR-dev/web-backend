package com.cocoa.web.service

import com.cocoa.web.model.Diary
import com.cocoa.web.repository.FormRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DiaryAnswerResolverTest {
    private val formRepository = mock<FormRepository>()
    private val resolver = DiaryAnswerResolver(formRepository)

    @Test
    fun `OPTION field with a known id resolves to its label`() {
        whenever(formRepository.resolveRefLabel("plot_id", "plot-a2")).thenReturn("แปลง A2")

        val resolved =
            resolver.resolve(
                Diary.AnswerField(fieldName = "plot_id", label = "แปลง", inputType = "OPTION", rawValue = "plot-a2"),
            )

        assertEquals("แปลง A2", resolved.value)
    }

    @Test
    fun `OPTION field with an unknown id falls back to the raw value instead of failing`() {
        whenever(formRepository.resolveRefLabel("fertilizer_id", "deleted-fert")).thenReturn(null)

        val resolved =
            resolver.resolve(
                Diary.AnswerField(fieldName = "fertilizer_id", label = "ปุ๋ย", inputType = "OPTION", rawValue = "deleted-fert"),
            )

        assertEquals("deleted-fert", resolved.value)
    }

    @Test
    fun `BOOLEAN true and false map to Thai without touching the ref table`() {
        val yes = resolver.resolve(Diary.AnswerField("is_quality_damage", "พบความเสียหาย", "BOOLEAN", "true"))
        val no = resolver.resolve(Diary.AnswerField("is_quality_damage", "พบความเสียหาย", "BOOLEAN", "false"))

        assertEquals("ใช่", yes.value)
        assertEquals("ไม่", no.value)
    }

    @Test
    fun `free-text fields pass through unresolved`() {
        val resolved = resolver.resolve(Diary.AnswerField("notes", "หมายเหตุ", "VARCHAR", "ใบเริ่มเหลืองที่ขอบ"))

        assertEquals("ใบเริ่มเหลืองที่ขอบ", resolved.value)
    }
}
