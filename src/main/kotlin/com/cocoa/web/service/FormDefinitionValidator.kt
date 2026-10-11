package com.cocoa.web.service

import com.cocoa.web.model.Handler

// US2-13 (docs-and-plan#241): the rules a form must satisfy before it's saved,
// so a researcher can no longer build a form that can never be submitted.
//
// Until now createForm/updateForm only checked "at least one section, at
// least one question". The audit of dev (design doc §2.5) found what that let
// through: 8 forms that never ask a NOT NULL column (every submission fails
// at INSERT -- the CB-2/3/4 bugs), 2 questions whose inputType can't fit their
// column (free text into a uuid), and 1 form asking the same field three
// times (the last answer silently wins).
//
// Pure: no repository, no Spring. FormService gathers the registry row, the
// table's columns and the field dependencies and hands them here, which keeps
// every rule unit-testable without a database.
object FormDefinitionValidator {
    // One question as the validator sees it. sectionOrder/questionOrder are
    // what decide "comes before" for dependencies: by section, then within it.
    data class QuestionSpec(
        val sectionOrder: Int,
        val questionOrder: Int,
        val label: String,
        val inputType: String,
        val fieldName: String?,
    )

    // Every problem found, not just the first -- a researcher fixing a form
    // shouldn't have to save five times to discover five problems.
    fun validate(
        registration: Handler.Registration,
        fields: List<Handler.Field>,
        dependencies: List<Handler.Dependency>,
        questions: List<QuestionSpec>,
    ): List<String> {
        val errors = mutableListOf<String>()
        val fieldByName = fields.associateBy { it.name }
        val asked = questions.filter { !it.fieldName.isNullOrBlank() }

        // inputType must be one the column can hold. A field_name that isn't a
        // column at all (e.g. "gis", "notes" on some tables) is allowed: its
        // answer is kept in form.response as before, it just isn't inserted.
        asked.forEach { question ->
            val field = fieldByName[question.fieldName] ?: return@forEach
            if (question.inputType !in field.allowedInputTypes) {
                val allowed = field.allowedInputTypes.joinToString(" / ").ifEmpty { "-" }
                errors +=
                    "คำถาม '${question.label}' (${question.fieldName}) ใช้ชนิด ${question.inputType} " +
                    "ไม่ได้ คอลัมน์นี้รับได้เฉพาะ $allowed"
            }
        }

        // Each field at most once: two questions writing one column means the
        // second answer silently overwrites the first.
        asked.groupBy { it.fieldName }
            .filterValues { it.size > 1 }
            .forEach { (fieldName, duplicates) ->
                val labels = duplicates.joinToString(", ") { "'${it.label}'" }
                errors += "ฟิลด์ $fieldName ถูกใช้ซ้ำ ${duplicates.size} คำถาม ($labels) ต้องมีได้คำถามเดียว"
            }

        // Every required column must be asked, or every submission fails at
        // INSERT. `required` already exempts a child handler's parent_field
        // (the chatbot's picker supplies it).
        val askedFields = asked.mapNotNull { it.fieldName }.toSet()
        val missing = fields.filter { it.required && it.name !in askedFields }.map { it.name }
        if (missing.isNotEmpty()) {
            errors +=
                "ตาราง ${registration.qualifiedTable} บังคับฟิลด์ ${missing.joinToString(", ")} " +
                "แต่ฟอร์มยังไม่มีคำถามสำหรับฟิลด์นี้"
        }

        // A dependent field needs its parent asked first, or the client has
        // nothing to filter its choices by (a plot list without a farm).
        // A parent the chatbot's picker supplies (the registry's parent_field)
        // counts as asked before everything.
        val position = asked.associate { it.fieldName to (it.sectionOrder to it.questionOrder) }
        asked.forEach { child ->
            val dependency = dependencies.firstOrNull { it.childField == child.fieldName } ?: return@forEach
            if (dependency.parentField == registration.parentField) return@forEach
            val parentPosition = position[dependency.parentField]
            val childPosition = position.getValue(child.fieldName)
            if (parentPosition == null || compareValues(parentPosition, childPosition) >= 0) {
                val parentLabel = asked.firstOrNull { it.fieldName == dependency.parentField }?.label
                val parentText = parentLabel?.let { "'$it' (${dependency.parentField})" } ?: dependency.parentField
                errors +=
                    "คำถาม '${child.label}' (${child.fieldName}) ต้องมีคำถาม $parentText อยู่ก่อน " +
                    "(${dependency.labelTh})"
            }
        }

        return errors
    }

    private fun compareValues(
        a: Pair<Int, Int>,
        b: Pair<Int, Int>,
    ): Int = compareValuesBy(a, b, { it.first }, { it.second })
}
