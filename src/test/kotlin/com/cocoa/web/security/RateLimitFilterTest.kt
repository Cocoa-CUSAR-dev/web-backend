package com.cocoa.web.security

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

// US3-2 #125 (F7/F4): unit test of the SSO rate limiter. The filter's wiring
// into the real chain (ahead of the service-key / JWT checks) is verified by
// the live pentest; here we prove the throttling logic itself.
class RateLimitFilterTest {
    private val filter = RateLimitFilter(limit = 3, windowSeconds = 60)

    private fun hit(
        chain: FilterChain,
        path: String = "/service/sso/tokens",
        ip: String = "203.0.113.9",
    ): Int {
        val request = MockHttpServletRequest("POST", path)
        request.servletPath = path
        request.remoteAddr = ip
        val response = MockHttpServletResponse()
        filter.doFilter(request, response, chain)
        return response.status
    }

    @Test
    fun `throttles an SSO endpoint past the limit`() {
        val chain = mock<FilterChain>()

        // First 3 pass through to the rest of the chain.
        repeat(3) {
            assertNotEquals(HttpStatus.TOO_MANY_REQUESTS.value(), hit(chain))
        }
        // The 4th is rejected with 429 and does not reach the chain.
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), hit(chain))

        verify(chain, times(3)).doFilter(any(), any())
    }

    @Test
    fun `counts each client IP separately`() {
        val chain = mock<FilterChain>()
        repeat(4) { hit(chain, ip = "198.51.100.1") }
        // A different IP is unaffected by the first IP's exhausted window.
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS.value(), hit(chain, ip = "198.51.100.2"))
    }

    @Test
    fun `does not touch non-SSO paths`() {
        val chain = mock<FilterChain>()
        // Well past the limit, but on an unrelated path -> never throttled.
        repeat(10) {
            assertNotEquals(HttpStatus.TOO_MANY_REQUESTS.value(), hit(chain, path = "/auth/login"))
        }
    }
}
