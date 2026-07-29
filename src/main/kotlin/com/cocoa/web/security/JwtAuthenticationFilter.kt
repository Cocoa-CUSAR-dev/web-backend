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

        val jwtCookie =
            cookieService.findCookie(jwtProperties.name, request) ?: run {
                filterChain.doFilter(request, response)
                return
            }

        val jwtToken =
            jwtCookie.value ?: run {
                filterChain.doFilter(request, response)
                return
            }

        if (jwtTokenService.isExpired(jwtToken)) {
            response.addCookie(cookieService.removeCookie(jwtProperties.name))
            filterChain.doFilter(request, response)
            return
        }

        val username = jwtTokenService.getUsername(jwtToken)
        val userDetails =
            username?.let { userDetailService.loadUserByUsername(it) } ?: run {
                response.addCookie(cookieService.removeCookie(jwtProperties.name))
                filterChain.doFilter(request, response)
                return
            }

        if (!jwtTokenService.isValid(jwtToken, userDetails)) {
            response.addCookie(cookieService.removeCookie(jwtProperties.name))
            filterChain.doFilter(request, response)
            return
        }

        updateContext(userDetails)
        filterChain.doFilter(request, response)
    }

    private fun isCurrentlyAuthenticated(): Boolean {
        return SecurityContextHolder.getContext().authentication != null
    }

    private fun updateContext(foundUser: UserPrincipal) {
        val authToken = UsernamePasswordAuthenticationToken(foundUser, null, foundUser.authorities)

        SecurityContextHolder.getContext().authentication = authToken
    }
}
