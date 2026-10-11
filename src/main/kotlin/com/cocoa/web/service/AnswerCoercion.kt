package com.cocoa.web.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.TextNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// US2-8 (docs-and-plan#172): turns what a researcher typed into the JSON
// value that goes back into form.response.answer.
//
// Two rules keep a correction from quietly corrupting the record:
//  1. The text must be valid for the question's input type (a whole number
//     for INT, an ISO date for DATE, ...), because the same answer JSON also
//     feeds the diary and the domain-table writers.
//  2. The JSON kind of the value already stored is kept. Some submissions
//     store "5" and others store 5; replacing one with the other would make
//     that field inconsistent with its neighbours.
//
// Choice-style questions (OPTION) and GEODATA are not editable here: their
// stored value is an id or a structure, and a free-text box can't produce a
// correct one without the choice list.
object AnswerCoercion {
    private val EDITABLE_TYPES = setOf("VARCHAR", "INT", "FLOAT", "DATE", "DATETIME", "BOOLEAN")

    fun isEditable(inputType: String): Boolean = inputType.uppercase() in EDITABLE_TYPES

    fun coerce(
        inputType: String,
        raw: String,
        existing: JsonNode?,
    ): JsonNode {
        val type = inputType.uppercase()
        require(type in EDITABLE_TYPES) { "Fields of type $inputType cannot be corrected here" }

        val text = raw.trim()
        require(text.isNotEmpty()) { "Corrected value must not be blank" }

        return when (type) {
            "INT" -> {
                val number = text.toLongOrNull() ?: throw IllegalArgumentException("Value must be a whole number")
                if (existing?.isNumber == true) LongNode.valueOf(number) else TextNode.valueOf(number.toString())
            }
            "FLOAT" -> {
                val number = text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw IllegalArgumentException("Value must be a number")
                if (existing?.isNumber == true) DoubleNode.valueOf(number) else TextNode.valueOf(text)
            }
            "BOOLEAN" -> {
                val flag =
                    when (text.lowercase()) {
                        "true" -> true
                        "false" -> false
                        else -> throw IllegalArgumentException("Value must be true or false")
                    }
                if (existing?.isBoolean == true) BooleanNode.valueOf(flag) else TextNode.valueOf(flag.toString())
            }
            "DATE" ->
                try {
                    TextNode.valueOf(LocalDate.parse(text).toString())
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("Value must be a date like 2026-09-27", e)
                }
            "DATETIME" ->
                try {
                    // ISO_LOCAL_DATE_TIME always prints the seconds; LocalDateTime.toString()
                    // drops them when zero, and mobile-backend's validator needs "T15:04:05".
                    TextNode.valueOf(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.parse(text.replace(' ', 'T'))))
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("Value must be a date and time like 2026-09-27T09:30:00", e)
                }
            else -> TextNode.valueOf(text)
        }
    }

    // How a stored node reads on screen; null for "no answer".
    fun display(node: JsonNode?): String? {
        if (node == null || node.isNull) return null
        return if (node.isContainerNode) node.toString() else node.asText()
    }
}
