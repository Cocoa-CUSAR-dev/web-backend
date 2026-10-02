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
    //
    // US3-2 #125 (F1): tokenType stamps a `token_type` claim so a token's
    // intended audience is part of the signed payload. SSO mint tokens carry
    // SSO_TOKEN_TYPE; normal session tokens pass null and carry no such claim.
    // exchangeForCookie accepts only SSO-typed tokens, and
    // JwtAuthenticationFilter refuses them as request credentials -- so a mint
    // token can no longer double as a full API bearer token.
    fun generate(
        userPrincipal: UserPrincipal,
        timeToLive: Long = jwtProperties.accessTokenExpiration,
        tokenType: String? = null,
    ): String {
        val currentTime = System.currentTimeMillis()
        val user = userPrincipal.getUser()

        val claims =
            Jwts.builder()
                .claims()
                .subject(user.username)
                .add("userId", user.userId.toString())
                .add("permissions", user.permissions)
        if (tokenType != null) {
            claims.add(TOKEN_TYPE_CLAIM, tokenType)
        }

        return claims
            .issuedAt(Date(currentTime))
            .expiration(Date(currentTime + timeToLive))
            .and()
            .signWith(secretKey)
            .compact()
    }

    // Null when the claim is absent -- i.e. a normal session token, or any
    // token issued before this claim existed.
    fun getTokenType(token: String): String? {
        return getAllClaims(token)?.get(TOKEN_TYPE_CLAIM, String::class.java)
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

    companion object {
        const val TOKEN_TYPE_CLAIM = "token_type"
        const val SSO_TOKEN_TYPE = "sso"
    }
}
