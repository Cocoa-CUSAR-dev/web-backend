package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.config.JwtProperties
import com.cocoa.web.model.User
import jakarta.servlet.http.Cookie
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuthenticationService(
    private val authManager: AuthenticationManager,
    private val cookieService: CookieService,
    private val jwtTokenService: JwtTokenService,
    private val jwtProperties: JwtProperties,
    private val userService: UserService,
    private val userDetailService: CustomUserDetailService,
): BaseService() {

    fun getUser(username: String): User.Entity {
        return userService.getUser(username)
    }

    fun registerUser(userInfo: User.Input.Create): UUID {
        return userService.addNewUser(userInfo, requiresPasswordReset = false)
    }

    fun authenticate(
        request: User.Request.Login,
    ): Cookie {
        val authToken = UsernamePasswordAuthenticationToken(
            request.username,
            request.password
        )

        authManager.authenticate(authToken)

        val user = userDetailService.loadUserByUsername(request.username)
        val jwtToken = jwtTokenService.generate(user)
        val jwtCookie = cookieService.createCookie(
            cookieName = jwtProperties.name,
            cookieValue = jwtToken,
            cookieMaxAge = jwtProperties.accessTokenExpiration.toInt()
        )

        return jwtCookie
    }
}