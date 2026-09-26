package com.cocoa.web.service

import com.cocoa.web.model.Diary
import com.cocoa.web.repository.FormRepository
import org.springframework.stereotype.Service

// US2-6 (docs-and-plan#131): turns a raw answered field (possibly a UUID
// for an OPTION field, or "true"/"false" for BOOLEAN) into what a farmer
// can actually read, before template rendering. BOOLEAN has no ref table
// (chatbot synthesizes its two choices -- see chatbot's
// src/conversation/service.py::_BOOLEAN_CHOICES), so it's mapped directly
// here rather than through FormRepository.resolveRefLabel.
@Service
class DiaryAnswerResolver(
    private val formRepository: FormRepository,
) {
    fun resolve(field: Diary.AnswerField): Diary.ResolvedField {
        val value =
            when (field.inputType) {
                "BOOLEAN" ->
                    when (field.rawValue) {
                        "true" -> "ใช่"
                        "false" -> "ไม่"
                        else -> field.rawValue
                    }
                // A ref id that no longer resolves (e.g. the plot/fertilizer
                // was deleted or renamed since this answer was submitted)
                // falls back to the raw id rather than failing the whole
                // diary -- an imperfect but honest rendering beats losing
                // the fact entirely.
                "OPTION" -> formRepository.resolveRefLabel(field.fieldName, field.rawValue) ?: field.rawValue
                else -> field.rawValue
            }

        return Diary.ResolvedField(
            fieldName = field.fieldName,
            label = field.label,
            value = value,
        )
    }

    fun resolveAll(fields: List<Diary.AnswerField>): List<Diary.ResolvedField> = fields.map(::resolve)
}
