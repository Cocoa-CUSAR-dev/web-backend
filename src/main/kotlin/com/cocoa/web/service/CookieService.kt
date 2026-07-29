package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Service

@Service
class CookieService : BaseService() {
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
    ): Cookie {
        val cookie =
            Cookie(cookieName, cookieValue).apply {
                isHttpOnly = true
//            secure = true
                path = cookiePath
                maxAge = cookieMaxAge
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
