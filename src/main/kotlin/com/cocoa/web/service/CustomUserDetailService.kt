package com.cocoa.web.service

import com.cocoa.web.repository.UserRepository
import com.cocoa.web.security.UserPrincipal
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailService(
    private val userRepository: UserRepository,
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserPrincipal {
        val user =
            userRepository.fetchUser(username)
                ?: throw UsernameNotFoundException("User does not exists")

        return UserPrincipal(user)
    }
}
