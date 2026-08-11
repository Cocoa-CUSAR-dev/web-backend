package com.cocoa.web.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

@SpringBootTest(
    classes = [JwtPropertiesTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "jwt.key=test-key-12345678901234567890",
        "jwt.name=jwt-name",
        "jwt.access-token-expiration=36000000",
        "jwt.refresh-token-expiration=86400000",
    ],
)
class JwtPropertiesTest {
    @org.springframework.beans.factory.annotation.Autowired
    lateinit var jwtProperties: JwtProperties

    @Test
    fun `binds all four fields from properties`() {
        assertEquals("test-key-12345678901234567890", jwtProperties.key)
        assertEquals("jwt-name", jwtProperties.name)
        assertEquals(36000000L, jwtProperties.accessTokenExpiration)
        assertEquals(86400000L, jwtProperties.refreshTokenExpiration)
    }

    /**
     * Minimal config that only enables [JwtProperties] binding. Pulls in
     * nothing else from the production codebase — no DataSource, no JOOQ,
     * no security. The `application-test.properties` profile is still active
     * because Spring Boot picks it up from src/test/resources.
     */
    @EnableConfigurationProperties(JwtProperties::class)
    class TestConfig
}
