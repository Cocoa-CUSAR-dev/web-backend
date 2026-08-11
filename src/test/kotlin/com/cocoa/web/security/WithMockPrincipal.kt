package com.cocoa.web.security

import com.cocoa.web.model.User
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.support.WithSecurityContext
import org.springframework.security.test.context.support.WithSecurityContextFactory
import java.time.LocalDateTime
import java.util.UUID

/**
 * Like @WithMockUser, but plants a real [UserPrincipal] as the authentication
 * principal. Plain @WithMockUser populates a generic Spring Security `User`,
 * which fails the `principal as UserPrincipal` cast in BaseController.getAuthenticatedUser().
 * Use this instead on any test that hits an endpoint calling getAuthenticatedUser().
 */
@Retention(AnnotationRetention.RUNTIME)
@WithSecurityContext(factory = WithMockPrincipal.Factory::class)
annotation class WithMockPrincipal(
    val username: String = "tester@example.com",
    val authorities: Array<String> = [],
) {
    class Factory : WithSecurityContextFactory<WithMockPrincipal> {
        override fun createSecurityContext(annotation: WithMockPrincipal): SecurityContext {
            val now = LocalDateTime.now()
            val user =
                User.Entity(
                    userId = UUID.randomUUID(),
                    username = annotation.username,
                    passwordHash = "irrelevant",
                    isPasswordReset = true,
                    roles = emptyList(),
                    permissions = annotation.authorities.toList(),
                    createdAt = now,
                    updatedAt = now,
                )
            val principal = UserPrincipal(user)
            val authorities = annotation.authorities.map { SimpleGrantedAuthority(it) }
            val authToken = UsernamePasswordAuthenticationToken(principal, null, authorities)

            return SecurityContextHolder.createEmptyContext().apply { authentication = authToken }
        }
    }
}
