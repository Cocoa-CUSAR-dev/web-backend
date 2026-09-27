package com.cocoa.web.controller

import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

/**
 * The MockMvc test in WebApplicationTests covers the healthy path against
 * the H2 instance that is up for the whole suite -- which means it can
 * never reach the failure branch. These cover that branch, where the
 * interesting behaviour is.
 */
class HealthControllerTest {
    private val dsl = mock<DSLContext>()
    private val controller = HealthController(dsl)

    @Test
    fun `reports ok when the database answers`() {
        whenever(dsl.fetchValue("SELECT 1")).thenReturn(1)

        val response = controller.health()

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mapOf("status" to "ok"), response.body)
    }

    @Test
    fun `reports unavailable without leaking the connection details`() {
        // A real Hikari/JDBC failure message looks like this one: it names
        // the host, port, database and user. The endpoint is
        // unauthenticated, so returning it would hand those out to anyone
        // who asks, precisely while the system is down.
        val leaky =
            "Connection to ep-cocoa-123.ap-southeast-1.aws.neon.tech:5432 refused. " +
                "database=cocoa, user=cocoa_owner"
        whenever(dsl.fetchValue("SELECT 1")) doThrow RuntimeException(leaky)

        val response = controller.health()

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals(mapOf("status" to "error"), response.body)

        val rendered = response.body.toString()
        assertFalse(rendered.contains("neon.tech"), "response must not name the database host")
        assertFalse(rendered.contains("cocoa_owner"), "response must not name the database user")
    }
}
