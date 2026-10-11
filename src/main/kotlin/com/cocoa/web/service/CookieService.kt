package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class CookieService(
    // Secure by default — the JWT cookie must not be sent over plain HTTP once
    // deployed publicly (BE-1 / M2). Only local dev, which runs over plain
    // http://localhost, needs to override this to false: browsers never send a
    // Secure cookie back over HTTP, so hardcoding true would break local login.
    @Value("\${cookie.secure:true}") private val cookieSecure: Boolean,
) : BaseService() {
    fun findCookie(
        cookieName: String,
        request: HttpServletRequest,
    ): Cookie? {
        val foundCookie = request.cookies?.find { it.name == cookieName }

        return foundCookie
    }

    fun isCookieExists(
        cookieName: String,
        request: HttpServletRequest,
    ): Boolean {
        return findCookie(cookieName, request) != null
    }

    fun createCookie(
        cookieName: String,
        cookieValue: String,
        cookiePath: String = "/",
        // 24 hours
        cookieMaxAge: Int = 24 * 60 * 60,
        // US3-2 #125 (F5): CSRF is disabled globally, so the session cookie must
        // carry SameSite itself. Lax lets normal top-level navigation (e.g. the
        // SSO deep link opening the web app) still send the cookie, while
        // blocking it on cross-site subrequests that drive CSRF.
        sameSite: String = "Lax",
    ): Cookie {
        val cookie =
            Cookie(cookieName, cookieValue).apply {
                isHttpOnly = true
                secure = cookieSecure
                path = cookiePath
                maxAge = cookieMaxAge
                setAttribute("SameSite", sameSite)
            }

        return cookie
    }

    fun removeCookie(cookieName: String): Cookie {
        val removedCookie =
            createCookie(
                cookieName = cookieName,
                cookieValue = "",
                cookiePath = "/",
                cookieMaxAge = 0,
            )

        return removedCookie
    }
}
