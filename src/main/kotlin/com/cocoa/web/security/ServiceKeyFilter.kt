package com.cocoa.web.security

import com.cocoa.web.config.ChatbotServiceProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

@Component
class ServiceKeyFilter(
    private val chatbotServiceProperties: ChatbotServiceProperties,
) : OncePerRequestFilter() {
    // Gates /service/** for trusted first-party services (currently just the
    // chatbot), not farmer/researcher sessions -- mirrors mobile-backend's
    // ServiceAuthMiddleware: proves "the caller knows the shared service
    // key," nothing more, never impersonates a specific user. Routes behind
    // this (e.g. GET /service/forms/{formId}) must only expose data that
    // isn't farmer-specific, so no per-caller ownership check is needed the
    // way mobile-backend's SubmitTaskForUser needs one for writes.
    //
    // /service/** is listed in SecurityConfig's publicEndpoints (permitAll)
    // -- this filter is the actual gate, not Spring Security's own layer.
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!request.servletPath.startsWith("/service/")) {
            filterChain.doFilter(request, response)
            return
        }

        val expected = chatbotServiceProperties.key
        if (expected.isBlank()) {
            // Fail closed: an unset key must never mean "let everyone in."
            response.sendError(
                HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "chatbot-service.key not configured",
            )
            return
        }

        val provided = request.getHeader("X-Service-Key")
        if (provided == null || !constantTimeEquals(provided, expected)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid or missing service key")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun constantTimeEquals(
        a: String,
        b: String,
    ): Boolean {
        return MessageDigest.isEqual(
            a.toByteArray(StandardCharsets.UTF_8),
            b.toByteArray(StandardCharsets.UTF_8),
        )
    }
}
