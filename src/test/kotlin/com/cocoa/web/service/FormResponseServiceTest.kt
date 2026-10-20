package com.cocoa.web.service

import com.cocoa.web.base.PageRequest
import com.cocoa.web.repository.FormResponseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.UUID

class FormResponseServiceTest {
    private val repository: FormResponseRepository = mock()
    private val service = FormResponseService(repository)

    private val userId = UUID.randomUUID()

    // US5-1 #230: the page/size the controller resolved is handed straight to
    // the repository, which does the day-level DISTINCT + limit/offset. The
    // service adds no paging logic of its own.
    @Test
    fun `submission-days paging is passed straight to the repository`() {
        val page = PageRequest(page = 2, size = 10)
        val days = listOf(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 29))
        whenever(repository.fetchOwnSubmissionDays(userId, page)).thenReturn(days)

        assertEquals(days, service.getOwnSubmissionDays(userId, page))
        verify(repository).fetchOwnSubmissionDays(userId, page)
    }

    // The controller always passes a PageRequest; the default here just keeps
    // the method callable without one (first page of 50, the BE-9 default).
    @Test
    fun `submission-days defaults to the first page of 50 when none is given`() {
        whenever(repository.fetchOwnSubmissionDays(userId, PageRequest())).thenReturn(emptyList())

        service.getOwnSubmissionDays(userId)

        verify(repository).fetchOwnSubmissionDays(userId, PageRequest(page = 0, size = 50))
    }
}
