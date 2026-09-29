package com.cocoa.web.controller

import org.jooq.DSLContext
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("/public/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return try {
            dsl.fetchValue("SELECT 1")
            ResponseEntity.ok(mapOf("status" to "ok"))
        } catch (ex: Exception) {
            // Logged, not returned. This endpoint is unauthenticated, and a
            // JDBC failure message routinely carries the Neon host, port,
            // database name and username -- which would hand out the
            // connection details exactly when the system is broken and
            // someone is poking at it. An uptime monitor reads the status
            // code anyway; whoever is debugging has the log.
            logger.error("Health check failed: database ping did not succeed", ex)
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(mapOf("status" to "error"))
        }
    }
}
