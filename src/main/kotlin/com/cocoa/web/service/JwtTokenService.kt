package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.config.JwtProperties
import com.cocoa.web.security.UserPrincipal
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID

@Service
class JwtTokenService(
    private val jwtProperties: JwtProperties,
) : BaseService() {
    private val secretKey =
        Keys.hmacShaKeyFor(
            jwtProperties.key.toByteArray(),
        )

    // BE-4: userId and permissions ride along in the token itself so
    // JwtAuthenticationFilter can authorize a request without re-running
    // UserRepository.fetchUser()'s 4-table join on every single call --
    // that join only needs to happen once, here, at token issuance.
    fun generate(
        userPrincipal: UserPrincipal,
        timeToLive: Long = jwtProperties.accessTokenExpiration,
    ): String {
        val currentTime = System.currentTimeMillis()
        val user = userPrincipal.getUser()

        return Jwts.builder()
            .claims()
            .subject(user.username)
            .add("userId", user.userId.toString())
            .add("permissions", user.permissions)
            .issuedAt(Date(currentTime))
            .expiration(Date(currentTime + timeToLive))
            .and()
            .signWith(secretKey)
            .compact()
    }

    fun isValid(
        token: String,
        userDetails: UserDetails,
    ): Boolean {
        val username = getUsername(token)

        return username == userDetails.username
    }

    fun getUsername(token: String): String? {
        return getAllClaims(token)?.subject
    }

    // Null whenever the claim is missing (e.g. a token issued before this
    // change rolled out) -- callers fall back to the DB lookup in that case
    // rather than treating an old-but-still-valid token as invalid.
    fun getUserId(token: String): UUID? {
        val raw = getAllClaims(token)?.get("userId", String::class.java) ?: return null
        return try {
            UUID.fromString(raw)
        } catch (ex: IllegalArgumentException) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getPermissions(token: String): List<String>? {
        return getAllClaims(token)?.get("permissions", List::class.java) as? List<String>
    }

    fun isExpired(token: String): Boolean {
        val claims = getAllClaims(token) ?: return true

        return claims
            .expiration
            .before(Date(System.currentTimeMillis()))
    }

    private fun getAllClaims(token: String): Claims? {
        return try {
            val parser =
                Jwts.parser()
                    .verifyWith(secretKey)
                    .build()

            parser.parseSignedClaims(token).payload
        } catch (ex: io.jsonwebtoken.ExpiredJwtException) {
            null
        } catch (ex: io.jsonwebtoken.JwtException) {
            // Malformed, unsupported, or bad-signature tokens — treat the
            // same as "no valid claims" rather than letting the parser
            // exception surface as an unhandled 500.
            null
        } catch (ex: IllegalArgumentException) {
            // JJWT throws this for a null/blank/non-JWT-shaped compact string.
            null
        }
    }
}
