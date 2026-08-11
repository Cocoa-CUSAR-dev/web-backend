package com.cocoa.web.jooq

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.sql.Date
import java.time.LocalDate

class LocalDateConverterTest {
    private val converter = LocalDateConverter()

    @Test
    fun `from converts sql Date to LocalDate`() {
        val date = Date.valueOf("2026-07-29")
        val result = converter.from(date)

        assertEquals(LocalDate.of(2026, 7, 29), result)
    }

    @Test
    fun `from returns null for null input`() {
        assertNull(converter.from(null))
    }

    @Test
    fun `to converts LocalDate to sql Date`() {
        val localDate = LocalDate.of(2026, 7, 29)
        val result = converter.to(localDate)

        assertEquals(Date.valueOf("2026-07-29"), result)
    }

    @Test
    fun `to returns null for null input`() {
        assertNull(converter.to(null))
    }

    @Test
    fun `round-trip preserves the date`() {
        val original = LocalDate.of(2026, 1, 1)
        val sql = converter.to(original)
        val back = converter.from(sql)

        assertEquals(original, back)
    }
}
