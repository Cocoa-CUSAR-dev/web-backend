package com.cocoa.web.repository

import com.cocoa.web.repository.FormResponseRepository.Companion.FARMER_ZONE
import com.cocoa.web.repository.FormResponseRepository.Companion.farmerDayRangeInUtc
import com.cocoa.web.repository.FormResponseRepository.Companion.toFarmerLocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

// Confirmed live 2026-09-18: form.response.submitted_at is written as UTC
// wall-clock (mobile-backend's Docker container clock), but "today" must
// mean the farmer's own calendar day (Asia/Bangkok, UTC+7). A submission at
// 00:17 Bangkok time is stored as 17:17 the PREVIOUS UTC day -- these tests
// pin the exact scenario that made diary generation silently find nothing.
class FormResponseRepositoryTest {
    @Test
    fun `a submission just after midnight Bangkok time belongs to the new day, not UTC's previous day`() {
        // 2026-09-18 00:17 Bangkok = 2026-09-17 17:17 UTC
        val submittedAtUtc = LocalDateTime.of(2026, 9, 17, 17, 17)

        assertEquals(LocalDate.of(2026, 9, 18), submittedAtUtc.toFarmerLocalDate())
    }

    @Test
    fun `a submission just before midnight Bangkok time still belongs to today`() {
        // 2026-09-17 23:59 Bangkok = 2026-09-17 16:59 UTC
        val submittedAtUtc = LocalDateTime.of(2026, 9, 17, 16, 59)

        assertEquals(LocalDate.of(2026, 9, 17), submittedAtUtc.toFarmerLocalDate())
    }

    @Test
    fun `farmerDayRangeInUtc for Bangkok Sept 18 covers UTC Sept 17 17-00 to Sept 18 17-00`() {
        val (start, end) = LocalDate.of(2026, 9, 18).farmerDayRangeInUtc()

        assertEquals(LocalDateTime.of(2026, 9, 17, 17, 0), start)
        assertEquals(LocalDateTime.of(2026, 9, 18, 17, 0), end)
    }

    @Test
    fun `a submission that fell through the old bug now falls inside the correct range`() {
        // The exact response that triggered "no submissions to generate a
        // diary from" before this fix.
        val submittedAtUtc = LocalDateTime.of(2026, 9, 17, 17, 17, 45)
        val (start, end) = LocalDate.of(2026, 9, 18).farmerDayRangeInUtc()

        assertEquals(true, submittedAtUtc >= start && submittedAtUtc < end)
    }

    @Test
    fun `FARMER_ZONE is Asia by Bangkok`() {
        assertEquals("Asia/Bangkok", FARMER_ZONE.id)
    }
}
