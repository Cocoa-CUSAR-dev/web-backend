package com.cocoa.web.observability

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.core.io.ClassPathResource
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * Plain unit tests -- no Spring context, so these stay fast. The MockMvc
 * tests in WebApplicationTests cover the response header end to end; what
 * is covered here is the half that header tests cannot see: whether the
 * MDC is actually populated for the duration of the request, and whether
 * the log pattern that consumes it still exists.
 */
class RequestIdFilterTest {
    private val filter = RequestIdFilter()

    /** Runs the filter and hands back whatever the MDC held mid-chain. */
    private fun mdcDuringChain(inbound: String? = null): Pair<String?, MockHttpServletResponse> {
        val request =
            MockHttpServletRequest().apply {
                inbound?.let { addHeader("X-Request-Id", it) }
            }
        val response = MockHttpServletResponse()
        var seen: String? = null
        val chain = FilterChain { _, _ -> seen = MDC.get("request_id") }

        filter.doFilter(request, response, chain)
        return seen to response
    }

    @Test
    fun `request id is in the MDC while the chain runs`() {
        val (seen, response) = mdcDuringChain()

        assertNotNull(seen, "request_id should be in the MDC for the whole request")
        assertEquals(response.getHeader("X-Request-Id"), seen)
    }

    @Test
    fun `inbound request id is the one put in the MDC`() {
        val (seen, _) = mdcDuringChain(inbound = "inbound-id-123")

        assertEquals("inbound-id-123", seen)
    }

    @Test
    fun `MDC is cleared afterwards so the pooled thread does not leak it`() {
        mdcDuringChain(inbound = "inbound-id-123")

        // Tomcat reuses worker threads; a value left behind here would be
        // attributed to whoever's request lands on this thread next.
        assertNull(MDC.get("request_id"))
    }

    @Test
    fun `logging pattern still promotes the MDC key onto every log line`() {
        // Regression guard: putting a value in the MDC does nothing on its
        // own. If this line is ever dropped from application.properties,
        // X-Request-Id keeps propagating between services and the logs
        // silently stop being correlatable -- a failure with no symptom
        // until someone actually needs to trace a request.
        val properties =
            ClassPathResource("application.properties")
                .inputStream.bufferedReader()
                .readText()

        assertTrue(
            properties.lineSequence().any {
                it.startsWith("logging.pattern.level=") && it.contains("%X{request_id")
            },
            "application.properties must keep a logging.pattern.level containing %X{request_id}",
        )
    }
}
