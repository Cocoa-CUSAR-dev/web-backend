package com.cocoa.web.exception

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CustomExceptionTest {
    @Test
    fun `EntityNotFoundException has default message`() {
        assertEquals("Entity not found", EntityNotFoundException().message)
    }

    @Test
    fun `EntityNotFoundException accepts custom message`() {
        val ex = EntityNotFoundException("Custom missing")
        assertEquals("Custom missing", ex.message)
    }

    @Test
    fun `InvalidTableCodeException has default message`() {
        assertEquals("Invalid table code", InvalidTableCodeException().message)
    }

    @Test
    fun `LocationHierarchyException has default message`() {
        assertEquals(
            "The provided location hierarchy is inconsistent",
            LocationHierarchyException().message,
        )
    }

    @Test
    fun `UserNotFoundException inherits from EntityNotFoundException`() {
        val ex = UserNotFoundException()
        assertEquals("User not found", ex.message)
        assertEquals(true, ex is EntityNotFoundException)
    }

    @Test
    fun `PermissionDeniedException has default message`() {
        assertEquals(
            "You do not have permission to perform this action",
            PermissionDeniedException().message,
        )
    }

    @Test
    fun `UnsupportedSheetException has default message`() {
        assertEquals(
            "The requested sheet type is not supported",
            UnsupportedSheetException().message,
        )
    }

    @Test
    fun `EmptyExportException has default message`() {
        assertEquals(
            "No data available for the requested export",
            EmptyExportException().message,
        )
    }
}
