package com.cocoa.web.controller

import org.jooq.DSLContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Real health check (same shape as mobile-backend's GO-5 public/health
 * endpoint) -- pings the database instead of returning a static 200
 * regardless of whether the app can actually serve traffic. Public (see
 * SecurityConfig's public-prefix rule) since an uptime monitor has no
 * farmer/researcher session to authenticate with.
 */
@RestController
class HealthController(
    private val dsl: DSLContext,
) {
    @GetMapping("/public/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return try {
            dsl.fetchValue("SELECT 1")
            ResponseEntity.ok(mapOf("status" to "ok"))
        } catch (ex: Exception) {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("status" to "error", "error" to (ex.message ?: "unknown")))
        }
    }
}
