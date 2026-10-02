package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.config.JwtProperties
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.exception.InvalidSsoTokenException
import com.cocoa.web.repository.UserRepository
import com.cocoa.web.security.UserPrincipal
import jakarta.servlet.http.Cookie
import org.springframework.stereotype.Service
import java.util.UUID

// US2-6: mintToken and exchangeForCookie are two separate steps, like a
// short OAuth code redeemed for a real session, rather than handing the
// LINE deep-link a full-length cookie directly -- the token that rides in
// a LINE push message (screenshottable, forwardable) only stays valid for
// TOKEN_TTL_MS regardless of who ends up holding it, while the farmer still
// gets a normal full-length session once they actually open the link. Not
// single-use (no server-side state to track that) -- the short TTL is the
// mitigation, which is proportionate for a link that only unlocks the
// farmer's own diary history.
//
// TOKEN_TTL_MS started at 2 minutes but that's shorter than the real gap
// between "chatbot pushes the card" and "farmer notices the LINE
// notification and taps it" -- confirmed live 2026-09-17, a token routinely
// expired before it was ever opened. 15 minutes is still far short of a
// normal session, just long enough to survive that human-in-the-loop delay.
@Service
class SsoService(
    private val userRepository: UserRepository,
    private val jwtTokenService: JwtTokenService,
    private val cookieService: CookieService,
    private val jwtProperties: JwtProperties,
) : BaseService() {
    fun mintToken(userId: UUID): String {
        val user =
            userRepository.fetchUserById(userId)
                ?: throw EntityNotFoundException("No user found for id=$userId")

        return jwtTokenService.generate(
            UserPrincipal(user),
            timeToLive = TOKEN_TTL_MS,
            tokenType = JwtTokenService.SSO_TOKEN_TYPE,
        )
    }

    fun exchangeForCookie(token: String): Cookie {
        if (jwtTokenService.isExpired(token)) {
            throw InvalidSsoTokenException()
        }

        // US3-2 #125 (F1): only a token minted as an SSO token may be redeemed
        // here. A normal session token (no token_type) or any other JWT is
        // refused, so this endpoint can't be used to launder an unrelated token
        // into a fresh session.
        if (jwtTokenService.getTokenType(token) != JwtTokenService.SSO_TOKEN_TYPE) {
            throw InvalidSsoTokenException()
        }

        val username = jwtTokenService.getUsername(token) ?: throw InvalidSsoTokenException()
        val user = userRepository.fetchUser(username) ?: throw InvalidSsoTokenException()

        val sessionToken = jwtTokenService.generate(UserPrincipal(user))
        return cookieService.createCookie(
            cookieName = jwtProperties.name,
            cookieValue = sessionToken,
            cookieMaxAge = jwtProperties.accessTokenExpiration.toInt(),
        )
    }

    companion object {
        // Same trust boundary as /service/diaries/generate -- the chatbot
        // already fully controls which userId that endpoint acts on, so
        // this doesn't grant it anything new, just a short window to prove
        // it to the browser.
        const val TOKEN_TTL_MS = 900_000L
    }
}
