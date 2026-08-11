package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.config.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.util.Date

@Service
class JwtTokenService(
    private val jwtProperties: JwtProperties,
) : BaseService() {
    private val secretKey =
        Keys.hmacShaKeyFor(
            jwtProperties.key.toByteArray(),
        )

    fun generate(
        userDetails: UserDetails,
        timeToLive: Long = jwtProperties.accessTokenExpiration,
    ): String {
        val currentTime = System.currentTimeMillis()

        return Jwts.builder()
            .claims()
            .subject(userDetails.username)
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
