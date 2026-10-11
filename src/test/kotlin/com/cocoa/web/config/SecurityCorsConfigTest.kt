package com.cocoa.web.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

// US3-2 #125 (F8): with credentials allowed, CORS must not use wildcard methods
// or headers. Red before the fix (both were ["*"]), green after.
class SecurityCorsConfigTest {
    private fun corsFor(origins: List<String>): CorsConfiguration {
        val source = SecurityConfig(origins, mock<AuthenticationProvider>()).corsConfigurationSource()
        return (source as UrlBasedCorsConfigurationSource).corsConfigurations["/**"]!!
    }

    @Test
    fun `CORS allows credentials but not wildcard methods or headers`() {
        val cors = corsFor(listOf("https://app.example.com"))

        assertEquals(true, cors.allowCredentials)
        assertNotEquals(listOf("*"), cors.allowedMethods, "methods must be an explicit allow-list")
        assertNotEquals(listOf("*"), cors.allowedHeaders, "headers must be an explicit allow-list")
    }
}
