package com.cocoa.web.observability

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

private const val REQUEST_ID_HEADER = "X-Request-Id"
private const val MDC_KEY = "request_id"

/**
 * X-2e: accepts an inbound X-Request-Id (e.g. forwarded by chatbot or
 * mobile-backend calling in) or generates a new one, puts it in the SLF4J
 * MDC so application.properties' logging.pattern.level promotes it to
 * every log line for this request, and echoes it back in the response
 * header so the caller can correlate its own logs with this service's.
 *
 * The MDC half only pays off because of that pattern -- without it the
 * value is set and never printed, and the header alone correlates
 * nothing. RequestIdFilterTest guards the pattern for that reason.
 *
 * @Order(HIGHEST_PRECEDENCE) runs this before Spring Security's own filter
 * chain (registered at SecurityProperties.DEFAULT_FILTER_ORDER, -100), so
 * request_id is already in the MDC by the time JwtAuthenticationFilter
 * or ServiceKeyFilter log anything.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId =
            request.getHeader(REQUEST_ID_HEADER)?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString()

        MDC.put(MDC_KEY, requestId)
        response.setHeader(REQUEST_ID_HEADER, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }
}
