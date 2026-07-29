package com.cocoa.web.jooq

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Time
import java.time.LocalTime

class LocalTimeConverterTest {
    private val converter = LocalTimeConverter()

    @Test
    fun `from converts sql Time to LocalTime`() {
        val time = Time.valueOf("12:34:56")
        val result = converter.from(time)

        assertEquals(LocalTime.of(12, 34, 56), result)
    }

    @Test
    fun `from returns null for null input`() {
        assertNull(converter.from(null))
    }

    @Test
    fun `to converts LocalTime to sql Time`() {
        val localTime = LocalTime.of(12, 34, 56)
        val result = converter.to(localTime)

        assertEquals(Time.valueOf("12:34:56"), result)
    }

    @Test
    fun `to returns null for null input`() {
        assertNull(converter.to(null))
    }

    @Test
    fun `round-trip preserves the time`() {
        val original = LocalTime.of(9, 30, 0)
        val sql = converter.to(original)
        val back = converter.from(sql)

        assertEquals(original, back)
    }
}
