package com.cocoa.web.controller

import com.cocoa.web.config.JwtProperties
import com.cocoa.web.model.User
import com.cocoa.web.repository.SsoUsedTokenRepository
import com.cocoa.web.repository.UserRepository
import com.cocoa.web.service.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import java.util.UUID

// US3-2 / #125: the adversarial counterpart to CrossSurfaceIdentityE2ETest.
// That test proves SSO works when used correctly; this one deliberately
// misuses it, the way an attacker holding a leaked/forwarded SSO deep-link
// token would, and asserts the system refuses.
//
// Written red-first: each test encodes the SECURE behaviour we want, so it
// fails against today's code and passes once the matching fix lands.
//   F1 -> a mint token must not double as an API bearer credential
//   F3 -> a mint token must be single-use
//   F5 -> the session cookie must carry a SameSite attribute
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:sso125;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class SsoSecurityTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var jwtProperties: JwtProperties

    @MockBean
    lateinit var userRepository: UserRepository

    @MockBean
    lateinit var userService: UserService

    // F1/F5 don't depend on the single-use store; F3 drives it explicitly.
    // The real repo would hit a table H2 doesn't have, so it's mocked here and
    // the real atomic SQL is proven by the live after-probe against Postgres.
    @MockBean
    lateinit var ssoUsedTokenRepository: SsoUsedTokenRepository

    private fun farmer(username: String) =
        User.Entity(
            userId = UUID.randomUUID(),
            username = username,
            passwordHash = "irrelevant",
            isPasswordReset = true,
            roles = listOf("farmer"),
            permissions = listOf("read:profile:own"),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

    private fun stub(user: User.Entity) {
        whenever(userRepository.fetchUserById(user.userId)).thenReturn(user)
        whenever(userRepository.fetchUser(user.username)).thenReturn(user)
        whenever(userService.getUserDetail(user.userId)).thenReturn(
            User.Detail(
                userId = user.userId,
                email = user.username,
                firstName = null,
                lastName = null,
                organization = null,
                isPasswordReset = user.isPasswordReset,
                isRequiresPasswordReset = false,
                roles = user.roles,
            ),
        )
    }

    private fun mint(user: User.Entity): String {
        val body = objectMapper.writeValueAsString(mapOf("userId" to user.userId))
        val response =
            mockMvc.perform(
                post("/service/sso/tokens")
                    .header("X-Service-Key", "test-service-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        return objectMapper.readTree(response).get("value").asText()
    }

    // --- F1: an SSO mint token must not be usable as an API bearer credential ---
    @Test
    fun `an SSO mint token is rejected as an Authorization Bearer credential`() {
        val farmer = farmer("farmer-f1@example.com")
        stub(farmer)
        val ssoToken = mint(farmer)

        // An attacker who intercepts the deep-link token tries to skip the
        // exchange and call the API directly as the farmer. The mint token is
        // only meant to be redeemed at /auth/sso/exchange, never to authorize
        // a request on its own.
        mockMvc.perform(
            get("/auth/me").header("Authorization", "Bearer $ssoToken"),
        ).andExpect(status().isUnauthorized)
    }

    // --- F3: an SSO mint token must be single-use ---
    @Test
    fun `an SSO mint token cannot be exchanged twice`() {
        val farmer = farmer("farmer-f3@example.com")
        stub(farmer)
        // First redemption claims the jti (true); the replay finds it already
        // used (false) -- exactly what the atomic INSERT ... ON CONFLICT returns.
        whenever(ssoUsedTokenRepository.markUsedIfFirstTime(any(), any())).thenReturn(true, false)
        val ssoToken = mint(farmer)

        val firstBody = objectMapper.writeValueAsString(mapOf("token" to ssoToken))
        mockMvc.perform(
            post("/auth/sso/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstBody),
        ).andExpect(status().isOk)

        // Replaying the same token (e.g. a forwarded LINE message opened by
        // someone else) must not mint a second session.
        val secondBody = objectMapper.writeValueAsString(mapOf("token" to ssoToken))
        mockMvc.perform(
            post("/auth/sso/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondBody),
        ).andExpect(status().isUnauthorized)
    }

    // --- F5: the session cookie must carry a SameSite attribute ---
    @Test
    fun `the session cookie set on exchange carries a SameSite attribute`() {
        val farmer = farmer("farmer-f5@example.com")
        stub(farmer)
        whenever(ssoUsedTokenRepository.markUsedIfFirstTime(any(), any())).thenReturn(true)
        val ssoToken = mint(farmer)

        val body = objectMapper.writeValueAsString(mapOf("token" to ssoToken))
        val cookie =
            mockMvc.perform(
                post("/auth/sso/exchange")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
                .andExpect(status().isOk)
                .andReturn()
                .response
                .getCookie(jwtProperties.name)
                ?: error("exchange did not set a ${jwtProperties.name} cookie")

        // The app's responsibility is to stamp SameSite on the cookie it hands
        // the container; Tomcat then serialises it into the Set-Cookie header
        // (MockMvc doesn't run that serialiser, so we assert the attribute the
        // app set, and confirm the real header in the live after-probe).
        assertTrue(
            cookie.getAttribute("SameSite").equals("Lax", ignoreCase = true),
            "session cookie must set SameSite=Lax to blunt CSRF (CSRF is disabled globally); " +
                "got SameSite=${cookie.getAttribute("SameSite")}",
        )
    }
}
