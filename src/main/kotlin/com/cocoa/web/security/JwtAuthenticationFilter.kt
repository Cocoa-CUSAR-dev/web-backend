package com.cocoa.web.security

import com.cocoa.web.config.JwtProperties
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
                try {
                    userDetailService.loadUserByUsername(it)
                } catch (ex: UsernameNotFoundException) {
                    null
                }
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
