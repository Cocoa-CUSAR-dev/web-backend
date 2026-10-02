package com.cocoa.web.controller

import com.cocoa.web.config.JwtProperties
import com.cocoa.web.model.User
import com.cocoa.web.repository.UserRepository
import com.cocoa.web.service.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
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
}
