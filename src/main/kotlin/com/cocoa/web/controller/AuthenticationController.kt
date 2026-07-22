package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.config.JwtProperties
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.User
import com.cocoa.web.model.User.toCreate
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.AuthenticationService
import com.cocoa.web.service.CookieService
import com.cocoa.web.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "User registration, login, and session management")
class AuthenticationController(
    private val authenticationService: AuthenticationService,
    private val userService: UserService,
    private val cookieService: CookieService,
    private val jwtProperties: JwtProperties,
): BaseController() {

    @PreAuthorize("hasAuthority('read:profile:own')")
    @Operation(summary = "Get current user profile")
    @GetMapping("/me")
    fun getCurrentUser(): ResponseEntity<ApiResponse<User.Detail>> {
        val user = getAuthenticatedUser()
        return userService.getUserDetail(user.userId).toResponseEntity(HttpStatus.OK)
    }

    @Operation(summary = "Self-registration")
    @PostMapping("/register")
    fun register(
        @RequestBody userRegisterRequest: User.Request.Register,
    ): ResponseEntity<ApiResponse<String>> {
        authenticationService.registerUser(userRegisterRequest.toCreate())
        return "User registered successfully".toResponseEntity(HttpStatus.CREATED)
    }

    @Operation(summary = "User login", description = "Authenticates user and sets a JWT cookie.")
    @PostMapping("/login")
    fun login(
        @RequestBody loginRequest: User.Request.Login,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<ApiResponse<String>> {
        cookieService.findCookie(jwtProperties.name, request)?.let {
            throw RuntimeException("Already logged in")
        }
        val jwtCookie = authenticationService.authenticate(loginRequest)
        response.addCookie(jwtCookie)
        return "Logged in successfully".toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "User logout")
    @GetMapping("/logout")
    fun logout(response: HttpServletResponse): ResponseEntity<ApiResponse<String>> {
        val removedCookie = cookieService.removeCookie(jwtProperties.name)
        response.addCookie(removedCookie)
        return "Logout successfully".toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('update:profile:own')")
    @Operation(summary = "Reset password", description = "Updates password and clears current session.")
    @PatchMapping("/reset-password")
    fun resetPassword(
        @RequestBody request: User.Request.ResetPassword,
        response: HttpServletResponse,
    ): ResponseEntity<ApiResponse<String>> {
        val currentUser = getAuthenticatedUser()
        userService.resetPassword(currentUser.userId, request.newPassword)
        val removedCookie = cookieService.removeCookie(jwtProperties.name)
        response.addCookie(removedCookie)
        return "Password reset successfully, please log in again".toResponseEntity(HttpStatus.OK)
    }
}