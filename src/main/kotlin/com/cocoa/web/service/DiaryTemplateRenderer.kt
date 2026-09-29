package com.cocoa.web.service

import com.cocoa.web.model.Diary

// US2-6 (docs-and-plan#132): "template วางโครงก่อน, LLM เกลาสำนวนเท่านั้น" --
// this is the template half. It only echoes resolved facts back as a plain
// bullet list; it can never invent a value that wasn't in `fields`, which
// is exactly what makes DiaryFactGuard's check against the LLM's output
// meaningful (the template output IS the ground truth being guarded).
object DiaryTemplateRenderer {
    private const val PLOT_FIELD_NAME = "plot_id"
    private const val WHOLE_FARM_LINE = "- ขอบเขต: ไม่ได้ระบุแปลงที่เฉพาะเจาะจง (ถือว่าทำทั้งฟาร์ม)"

    fun render(fields: List<Diary.ResolvedField>): String {
        if (fields.isEmpty()) return ""

        val lines = fields.map { "- ${it.label}: ${it.value}" }.toMutableList()

        // #135: an unanswered plot_id means "the whole farm", not "nothing
        // to say about location" -- fields omits it entirely (see
        // FormResponseRepository.fetchAnswerFieldsForUserAndDate, which
        // drops blank answers before they ever become an AnswerField), so
        // its ABSENCE from the list is what this checks for.
        val hasPlot = fields.any { it.fieldName == PLOT_FIELD_NAME }
        if (!hasPlot) {
            lines.add(0, WHOLE_FARM_LINE)
        }

        return lines.joinToString("\n")
    }
}
