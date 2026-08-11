package com.cocoa.web.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class ApiResponseTest {
    @Test
    fun `toApiResponse wraps value without error`() {
        val resp = "hello".toApiResponse()

        assertEquals("hello", resp.value)
        assertNull(resp.error)
    }

    @Test
    fun `Exception toApiResponse wraps error with null value`() {
        val resp: ApiResponse<Unit> = RuntimeException("oops").toApiResponse()

        assertNull(resp.value)
        assertEquals("oops", resp.error)
    }

    @Test
    fun `toResponseEntity sets body and status`() {
        val entity = "world".toResponseEntity(HttpStatus.CREATED)

        assertEquals(HttpStatus.CREATED, entity.statusCode)
        assertNotNull(entity.body)
        assertEquals("world", entity.body!!.value)
    }
}
