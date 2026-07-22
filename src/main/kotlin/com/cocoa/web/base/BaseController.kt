package com.cocoa.web.base

import com.cocoa.web.model.User
import com.cocoa.web.security.UserPrincipal
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

abstract class BaseController {
    private fun getAuthentication(): Authentication {
        val securityContext = SecurityContextHolder.getContext()
        return securityContext.authentication
    }
    
    protected fun getAuthenticatedUser(): User.Entity {
        val userPrincipal = getAuthentication().principal as UserPrincipal
        return userPrincipal.getUser()
    }
}