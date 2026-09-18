package com.cocoa.web.service

import com.cocoa.web.config.JwtProperties
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.exception.InvalidSsoTokenException
import com.cocoa.web.model.User
import com.cocoa.web.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.UUID

class SsoServiceTest {
    private val jwtProperties =
        JwtProperties(
            key = "test-signing-key-that-is-long-enough-for-hmac-sha",
            name = "jwt",
            accessTokenExpiration = 3_600_000L,
            refreshTokenExpiration = 86_400_000L,
        )
    private val jwtTokenService = JwtTokenService(jwtProperties)
    private val cookieService = CookieService(cookieSecure = true)
    private val userRepository = mock<UserRepository>()
    private val ssoService = SsoService(userRepository, jwtTokenService, cookieService, jwtProperties)

    private val user =
        User.Entity(
            userId = UUID.randomUUID(),
            username = "farmer@example.com",
            passwordHash = "irrelevant",
            isPasswordReset = true,
            roles = listOf("farmer"),
            permissions = listOf("read:response:own"),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
        )

    @Test
    fun `mintToken returns a token carrying the farmer's own userId`() {
        whenever(userRepository.fetchUserById(user.userId)).thenReturn(user)

        val token = ssoService.mintToken(user.userId)

        assertEquals(user.userId, jwtTokenService.getUserId(token))
    }

    @Test
    fun `mintToken fails for an unknown userId instead of minting a token for nobody`() {
        val unknownId = UUID.randomUUID()
        whenever(userRepository.fetchUserById(unknownId)).thenReturn(null)

        assertThrows(EntityNotFoundException::class.java) { ssoService.mintToken(unknownId) }
    }

    @Test
    fun `exchangeForCookie turns a valid mint token into a full session cookie`() {
        whenever(userRepository.fetchUserById(user.userId)).thenReturn(user)
        whenever(userRepository.fetchUser(user.username)).thenReturn(user)
        val token = ssoService.mintToken(user.userId)

        val cookie = ssoService.exchangeForCookie(token)

        assertEquals(jwtProperties.name, cookie.name)
        assertEquals(user.userId, jwtTokenService.getUserId(cookie.value))
    }

    @Test
    fun `exchangeForCookie rejects a malformed token instead of throwing an unhandled parser exception`() {
        assertThrows(InvalidSsoTokenException::class.java) { ssoService.exchangeForCookie("not-a-jwt") }
    }
}
