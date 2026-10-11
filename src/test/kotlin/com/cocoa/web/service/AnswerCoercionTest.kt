package com.cocoa.web.service

import com.fasterxml.jackson.databind.node.BooleanNode
import com.fasterxml.jackson.databind.node.DoubleNode
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.TextNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnswerCoercionTest {
    @Test
    fun `plain text types are editable and choice or geo types are not`() {
        listOf("VARCHAR", "INT", "FLOAT", "DATE", "DATETIME", "BOOLEAN", "int").forEach {
            assertTrue(AnswerCoercion.isEditable(it), it)
        }
        listOf("OPTION", "GEODATA", "").forEach {
            assertFalse(AnswerCoercion.isEditable(it), it)
        }
    }

    @Test
    fun `a blank value is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { AnswerCoercion.coerce("VARCHAR", "   ", null) }
    }

    @Test
    fun `an uneditable type is rejected even if the text looks fine`() {
        assertThrows(IllegalArgumentException::class.java) { AnswerCoercion.coerce("OPTION", "abc", null) }
    }

    @Test
    fun `varchar is trimmed and stored as text`() {
        assertEquals(TextNode.valueOf("ปุ๋ยคอก"), AnswerCoercion.coerce("VARCHAR", "  ปุ๋ยคอก  ", null))
    }

    @Test
    fun `int keeps the existing JSON kind - number stays a number, text stays text`() {
        assertEquals(7L, AnswerCoercion.coerce("INT", "7", IntNode.valueOf(5)).asLong())
        assertTrue(AnswerCoercion.coerce("INT", "7", IntNode.valueOf(5)).isNumber)

        val asText = AnswerCoercion.coerce("INT", "7", TextNode.valueOf("5"))
        assertTrue(asText.isTextual)
        assertEquals("7", asText.asText())
    }

    @Test
    fun `int with no existing value is stored as text like the chatbot does`() {
        assertEquals(TextNode.valueOf("12"), AnswerCoercion.coerce("INT", " 12 ", null))
        assertEquals(TextNode.valueOf("12"), AnswerCoercion.coerce("INT", "12", NullNode.instance))
    }

    @Test
    fun `int rejects decimals and words`() {
        assertThrows(IllegalArgumentException::class.java) { AnswerCoercion.coerce("INT", "1.5", null) }
        assertThrows(IllegalArgumentException::class.java) { AnswerCoercion.coerce("INT", "ห้า", null) }
    }

    @Test
    fun `float accepts decimals and keeps a numeric node when one was stored`() {
        assertEquals(DoubleNode.valueOf(12.5), AnswerCoercion.coerce("FLOAT", "12.5", DoubleNode.valueOf(3.0)))
        assertEquals(TextNode.valueOf("12.50"), AnswerCoercion.coerce("FLOAT", "12.50", TextNode.valueOf("3")))
    }

    @Test
    fun `float rejects non numbers, NaN and infinity`() {
        listOf("abc", "NaN", "Infinity").forEach {
            assertThrows(IllegalArgumentException::class.java, { AnswerCoercion.coerce("FLOAT", it, null) }, it)
        }
    }

    @Test
    fun `boolean accepts true and false in any case`() {
        assertEquals(BooleanNode.TRUE, AnswerCoercion.coerce("BOOLEAN", "TRUE", BooleanNode.FALSE))
        assertEquals(TextNode.valueOf("false"), AnswerCoercion.coerce("BOOLEAN", "False", TextNode.valueOf("true")))
        assertThrows(IllegalArgumentException::class.java) { AnswerCoercion.coerce("BOOLEAN", "yes", null) }
    }

    @Test
    fun `date must be ISO and datetime always carries seconds`() {
        assertEquals(TextNode.valueOf("2026-09-27"), AnswerCoercion.coerce("DATE", "2026-09-27", null))
        assertThrows(IllegalArgumentException::class.java) { AnswerCoercion.coerce("DATE", "27/09/2026", null) }
        assertThrows(IllegalArgumentException::class.java) { AnswerCoercion.coerce("DATE", "2026-02-30", null) }

        assertEquals(TextNode.valueOf("2026-09-27T09:30:00"), AnswerCoercion.coerce("DATETIME", "2026-09-27T09:30", null))
        assertEquals(TextNode.valueOf("2026-09-27T09:30:15"), AnswerCoercion.coerce("DATETIME", "2026-09-27 09:30:15", null))
        assertThrows(IllegalArgumentException::class.java) { AnswerCoercion.coerce("DATETIME", "tomorrow", null) }
    }

    @Test
    fun `display shows scalars as text, containers as JSON and absence as null`() {
        assertNull(AnswerCoercion.display(null))
        assertNull(AnswerCoercion.display(NullNode.instance))
        assertEquals("5", AnswerCoercion.display(IntNode.valueOf(5)))
        assertEquals("abc", AnswerCoercion.display(TextNode.valueOf("abc")))
        assertEquals("""{"lat":1}""", AnswerCoercion.display(JsonNodeFactory.instance.objectNode().put("lat", 1)))
    }
}
