package com.cocoa.web.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// US3-2 #125 (F7, and F4's abuse angle): a lightweight in-memory fixed-window
// rate limiter on the two SSO endpoints -- mint (/service/sso/tokens) and
// exchange (/auth/sso/exchange). It throttles replay/scan/abuse before any auth
// or DB work runs, so a leaked service key or a stolen deep-link token can't be
// hammered.
//
// In-memory and per-instance (not shared across replicas) -- proportionate for
// a single-instance deploy; swap the store for Redis/bucket4j if the service is
// ever scaled out. Runs ahead of the Spring Security chain (HIGHEST_PRECEDENCE)
// so the throttle applies before the service-key / JWT checks -- abuse is
// rejected before any auth or DB work, even for unauthenticated callers.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RateLimitFilter(
    @Value("\${ratelimit.sso.limit:20}") private val limit: Int,
    @Value("\${ratelimit.sso.window-seconds:60}") private val windowSeconds: Long,
) : OncePerRequestFilter() {
    private class Window(val resetAtMs: Long) {
        val count = AtomicInteger(0)
    }

    private val counters = ConcurrentHashMap<String, Window>()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return request.servletPath !in LIMITED_PATHS
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val key = "${request.servletPath}|${clientIp(request)}"
        val now = System.currentTimeMillis()
        val window =
            counters.compute(key) { _, existing ->
                if (existing == null || now >= existing.resetAtMs) {
                    Window(now + windowSeconds * 1000)
                } else {
                    existing
                }
            }!!

        if (window.count.incrementAndGet() > limit) {
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "rate limit exceeded")
            return
        }

        filterChain.doFilter(request, response)
    }

    // X-Forwarded-For's first hop is the real client when behind a trusted
    // proxy; fall back to the socket address otherwise.
    private fun clientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) forwarded.substringBefore(",").trim() else request.remoteAddr
    }

    companion object {
        private val LIMITED_PATHS = setOf("/service/sso/tokens", "/auth/sso/exchange")
    }
}
