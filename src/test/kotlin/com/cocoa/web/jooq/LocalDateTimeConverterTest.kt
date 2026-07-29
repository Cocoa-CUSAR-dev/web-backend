package com.cocoa.web.jooq

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.LocalDateTime

class LocalDateTimeConverterTest {
    private val converter = LocalDateTimeConverter()

    @Test
    fun `from converts sql Timestamp to LocalDateTime`() {
        val timestamp = Timestamp.valueOf("2026-07-29 12:34:56")
        val result = converter.from(timestamp)

        assertEquals(LocalDateTime.of(2026, 7, 29, 12, 34, 56), result)
    }

    @Test
    fun `from returns null for null input`() {
        assertNull(converter.from(null))
    }

    @Test
    fun `to converts LocalDateTime to sql Timestamp`() {
        val localDateTime = LocalDateTime.of(2026, 7, 29, 12, 34, 56)
        val result = converter.to(localDateTime)

        assertEquals(Timestamp.valueOf("2026-07-29 12:34:56"), result)
    }

    @Test
    fun `to returns null for null input`() {
        assertNull(converter.to(null))
    }

    @Test
    fun `round-trip preserves the date-time`() {
        val original = LocalDateTime.of(2026, 1, 1, 9, 30, 0)
        val sql = converter.to(original)
        val back = converter.from(sql)

        assertEquals(original, back)
    }
}
