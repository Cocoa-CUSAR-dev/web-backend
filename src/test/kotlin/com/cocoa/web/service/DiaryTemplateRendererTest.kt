package com.cocoa.web.service

import com.cocoa.web.model.Diary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiaryTemplateRendererTest {
    @Test
    fun `empty field list renders an empty string`() {
        assertEquals("", DiaryTemplateRenderer.render(emptyList()))
    }

    @Test
    fun `every resolved value appears verbatim -- the template never invents or drops a fact`() {
        val fields =
            listOf(
                Diary.ResolvedField("fertilizer_id", "ปุ๋ย", "สูตร 15-15-15"),
                Diary.ResolvedField("amount", "ปริมาณ", "2 กิโลกรัม"),
                Diary.ResolvedField("plot_id", "แปลง", "แปลง A2"),
            )

        val rendered = DiaryTemplateRenderer.render(fields)

        fields.forEach { assertTrue(rendered.contains(it.value), "missing value: ${it.value}") }
        assertEquals(fields.size, rendered.lines().size)
    }

    @Test
    fun `missing plot_id (#135 -- left blank) renders as the whole farm, not silence`() {
        val fields = listOf(Diary.ResolvedField("fertilizer_id", "ปุ๋ย", "สูตร 15-15-15"))

        val rendered = DiaryTemplateRenderer.render(fields)

        assertTrue(rendered.contains("ทั้งฟาร์ม"))
    }

    @Test
    fun `an answered plot_id does not also get the whole-farm line`() {
        val fields = listOf(Diary.ResolvedField("plot_id", "แปลง", "แปลง B1"))

        val rendered = DiaryTemplateRenderer.render(fields)

        assertFalse(rendered.contains("ทั้งฟาร์ม"))
    }
}
