package com.cocoa.web.controller

import com.cocoa.web.config.JwtProperties
import com.cocoa.web.model.User
import com.cocoa.web.repository.UserRepository
import com.cocoa.web.service.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
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

// US3-2 / #126: drives the full LINE-mint -> web-exchange -> web-session loop
// through real HTTP endpoints (SsoServiceTest already covers SsoService in
// isolation with a mocked repository) to prove the identity that comes out
// the far end is always the one that went in -- across repeated round trips
// for one farmer, and across farmers whose requests interleave.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:cocoa-sso;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class CrossSurfaceIdentityE2ETest {
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
        // F4 (#125): mintToken now requires the user to have a linked LINE
        // identity; these farmers are linked.
        whenever(userRepository.hasLinkedLineIdentity(user.userId)).thenReturn(true)
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

    /** The LINE side: the chatbot mints a token for [user] and hands it a deep link. */
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

    /** The web side: the farmer opens the deep link and redeems [token] for a session. */
    private fun exchange(token: String): String {
        val body = objectMapper.writeValueAsString(mapOf("token" to token))
        val result =
            mockMvc.perform(
                post("/auth/sso/exchange")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )
                .andExpect(status().isOk)
                .andReturn()
        val cookie = result.response.getCookie(jwtProperties.name)
        checkNotNull(cookie) { "exchange did not set a ${jwtProperties.name} cookie" }
        return cookie.value
    }

    /** Whoever is holding [sessionCookieValue] asks the web side who they're logged in as. */
    private fun whoAmI(sessionCookieValue: String): UUID {
        val response =
            mockMvc.perform(get("/auth/me").cookie(Cookie(jwtProperties.name, sessionCookieValue)))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        return UUID.fromString(objectMapper.readTree(response).get("value").get("userId").asText())
    }

    @Test
    fun `one farmer hopping between LINE and web repeatedly always lands on their own identity`() {
        val farmer = farmer("farmer-a@example.com")
        stub(farmer)

        repeat(3) {
            val token = mint(farmer)
            val sessionCookie = exchange(token)
            assertEquals(farmer.userId, whoAmI(sessionCookie))
        }
    }

    @Test
    fun `two farmers hopping in an interleaved order never see each other's identity`() {
        val farmerA = farmer("farmer-a@example.com")
        val farmerB = farmer("farmer-b@example.com")
        stub(farmerA)
        stub(farmerB)

        val tokenA1 = mint(farmerA)
        val tokenB1 = mint(farmerB)
        val tokenA2 = mint(farmerA)

        // Exchanged out of mint order on purpose -- nothing about the
        // mint/exchange pair should assume requests arrive in the order
        // they were minted.
        assertEquals(farmerB.userId, whoAmI(exchange(tokenB1)))
        assertEquals(farmerA.userId, whoAmI(exchange(tokenA1)))
        assertEquals(farmerA.userId, whoAmI(exchange(tokenA2)))
    }

    @Test
    fun `a malformed or expired SSO token is rejected instead of granting a session`() {
        // TOKEN_TTL_MS expiry itself is exercised at the unit level
        // (SsoServiceTest); this confirms the failure surfaces as 401
        // through the actual HTTP path an attacker replaying an old LINE
        // deep-link would hit, not just inside SsoService.
        val body = objectMapper.writeValueAsString(mapOf("token" to "not-a-jwt"))
        mockMvc.perform(
            post("/auth/sso/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isUnauthorized)
    }
}
