package com.cocoa.web.security

import com.cocoa.web.config.JwtProperties
import com.cocoa.web.model.User
import com.cocoa.web.service.CookieService
import com.cocoa.web.service.CustomUserDetailService
import com.cocoa.web.service.JwtTokenService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.LocalDateTime

@Component
class JwtAuthenticationFilter(
    private val jwtProperties: JwtProperties,
    private val jwtTokenService: JwtTokenService,
    private val cookieService: CookieService,
    private val userDetailService: CustomUserDetailService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (isCurrentlyAuthenticated()) {
            filterChain.doFilter(request, response)
            return
        }

        val (jwtToken, fromCookie) =
            resolveToken(request) ?: run {
                filterChain.doFilter(request, response)
                return
            }

        if (jwtTokenService.isExpired(jwtToken)) {
            if (fromCookie) response.addCookie(cookieService.removeCookie(jwtProperties.name))
            filterChain.doFilter(request, response)
            return
        }

        val username = jwtTokenService.getUsername(jwtToken)
        val userDetails =
            username?.let {
                buildPrincipalFromClaims(jwtToken, it) ?: loadPrincipalFromDatabase(it)
            } ?: run {
                if (fromCookie) response.addCookie(cookieService.removeCookie(jwtProperties.name))
                filterChain.doFilter(request, response)
                return
            }

        if (!jwtTokenService.isValid(jwtToken, userDetails)) {
            if (fromCookie) response.addCookie(cookieService.removeCookie(jwtProperties.name))
            filterChain.doFilter(request, response)
            return
        }

        updateContext(userDetails)
        filterChain.doFilter(request, response)
    }

    // BE-4: userId + permissions ride in the token (see JwtTokenService.generate),
    // so a request carrying a valid token can be authorized without hitting
    // UserRepository.fetchUser()'s 4-table join. Null on any missing/malformed
    // claim -- including a token issued before this change -- so the caller
    // falls back to the real DB lookup instead of treating it as invalid.
    private fun buildPrincipalFromClaims(
        jwtToken: String,
        username: String,
    ): UserPrincipal? {
        val userId = jwtTokenService.getUserId(jwtToken) ?: return null
        val permissions = jwtTokenService.getPermissions(jwtToken) ?: return null

        // Fields below this point are never read once a request is
        // authenticated (confirmed: every getAuthenticatedUser() call site
        // only reads .userId) -- they exist purely to satisfy User.Entity's
        // shape, not because a controller uses them.
        val placeholderTime = LocalDateTime.now()
        return UserPrincipal(
            User.Entity(
                userId = userId,
                username = username,
                passwordHash = "irrelevant",
                isPasswordReset = false,
                roles = emptyList(),
                permissions = permissions,
                createdAt = placeholderTime,
                updatedAt = placeholderTime,
            ),
        )
    }

    private fun loadPrincipalFromDatabase(username: String): UserPrincipal? {
        return try {
            userDetailService.loadUserByUsername(username)
        } catch (ex: UsernameNotFoundException) {
            null
        }
    }

    private fun isCurrentlyAuthenticated(): Boolean {
        return SecurityContextHolder.getContext().authentication != null
    }

    // Cookie is the browser-facing path (web app). The Bearer header is for
    // service-to-service calls forwarding a caller's own token — e.g. the
    // mobile backend proxying a farmer's JWT to read their assigned form.
    private fun resolveToken(request: HttpServletRequest): Pair<String, Boolean>? {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.removePrefix("Bearer ").trim() to false
        }

        val jwtCookie = cookieService.findCookie(jwtProperties.name, request) ?: return null
        val jwtToken = jwtCookie.value ?: return null
        return jwtToken to true
    }

    private fun updateContext(foundUser: UserPrincipal) {
        val authToken = UsernamePasswordAuthenticationToken(foundUser, null, foundUser.authorities)

        SecurityContextHolder.getContext().authentication = authToken
    }
}
