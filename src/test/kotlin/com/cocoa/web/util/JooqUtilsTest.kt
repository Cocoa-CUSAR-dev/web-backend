package com.cocoa.web.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.YearMonth

class JooqUtilsTest {
    @Test
    fun `generateMonthRange returns single month when from equals to`() {
        val range = generateMonthRange(YearMonth.of(2026, 1), YearMonth.of(2026, 1))

        assertEquals(1, range.size)
        assertEquals(YearMonth.of(2026, 1), range[0])
    }

    @Test
    fun `generateMonthRange returns inclusive range`() {
        val range = generateMonthRange(YearMonth.of(2026, 1), YearMonth.of(2026, 3))

        assertEquals(3, range.size)
        assertEquals(YearMonth.of(2026, 1), range[0])
        assertEquals(YearMonth.of(2026, 2), range[1])
        assertEquals(YearMonth.of(2026, 3), range[2])
    }

    @Test
    fun `generateMonthRange returns empty when from is after to`() {
        val range = generateMonthRange(YearMonth.of(2026, 6), YearMonth.of(2026, 1))

        assertTrue(range.isEmpty())
    }

    @Test
    fun `generateMonthRange crosses year boundary`() {
        val range = generateMonthRange(YearMonth.of(2026, 11), YearMonth.of(2027, 2))

        assertEquals(4, range.size)
        assertEquals(YearMonth.of(2026, 11), range[0])
        assertEquals(YearMonth.of(2026, 12), range[1])
        assertEquals(YearMonth.of(2027, 1), range[2])
        assertEquals(YearMonth.of(2027, 2), range[3])
    }
}
