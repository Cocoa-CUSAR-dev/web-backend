package com.cocoa.web.config

import com.cocoa.web.security.JwtAuthenticationFilter
import com.cocoa.web.security.ServiceKeyFilter
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    @Value("\${cors.origins:default}") val allowedOrigins: List<String>,
    private val authenticationProvider: AuthenticationProvider,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
        serviceKeyFilter: ServiceKeyFilter,
    ): DefaultSecurityFilterChain {
        val publicEndpoints =
            arrayOf(
                "/auth/login",
                "/auth/register",
                "/auth/sso/exchange",
                "/swagger-ui/**",
                "/swagger-ui/index.html",
                "/api-docs/**",
                "/public/**",
                "/error",
                // Not actually public -- ServiceKeyFilter is the real gate
                // (X-Service-Key). Listed here only so Spring Security's own
                // authorization layer doesn't also demand a farmer/researcher JWT.
                "/service/**",
            )

        return http
            .csrf { it.disable() }
            .cors { }
            .authorizeHttpRequests {
                it
                    .requestMatchers(*publicEndpoints).permitAll()
                    .anyRequest().authenticated()
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authenticationProvider(authenticationProvider)
            .exceptionHandling { exceptions ->
                exceptions.authenticationEntryPoint { request, response, authException ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized")
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(serviceKeyFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        // US3-2 #125 (F8): credentials are allowed, so methods and headers are an
        // explicit allow-list rather than "*". All browser traffic reaches this
        // backend through the web-app BFF (server-to-server, no CORS), so this
        // doesn't affect any real cross-origin call -- it just removes a risky
        // wildcard + credentials combination.
        if (allowedOrigins == listOf("default")) {
            logger.warn(
                "cors.origins is not configured (fell back to \"default\"); set CORS_ORIGINS " +
                    "to the web app's real origin(s) in each deployed environment",
            )
        }

        val configuration = CorsConfiguration()
        configuration.allowedOrigins = allowedOrigins
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("Content-Type", "Authorization", "X-Service-Key")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
