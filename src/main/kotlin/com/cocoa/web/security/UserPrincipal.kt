package com.cocoa.web.security

import com.cocoa.web.model.User
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class UserPrincipal(
    private val user: User.Entity,
): UserDetails {
    override fun getAuthorities() = user.permissions.map { it -> SimpleGrantedAuthority(it) }
    override fun getPassword() = user.passwordHash
    override fun getUsername() = user.username
    override fun isAccountNonExpired() = true
    override fun isAccountNonLocked() = true
    override fun isCredentialsNonExpired() = true
    override fun isEnabled() = true

    fun getUser(): User.Entity {
        return user
    }
}