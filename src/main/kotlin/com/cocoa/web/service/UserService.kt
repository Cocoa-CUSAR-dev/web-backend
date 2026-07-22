package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.User
import com.cocoa.web.repository.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
): BaseService() {

    fun getUser(username: String): User.Entity {
        return userRepository.fetchUser(username)
            ?:  throw EntityNotFoundException("User not found")
    }

    fun getUserDetail(userId: UUID): User.Detail {
        return userRepository.fetchUserDetail(userId)
            ?: throw EntityNotFoundException("User not found")
    }

    fun addNewUser(
        userInfo: User.Input.Create,
        requiresPasswordReset: Boolean = false,
        roleName: String? = null,
    ): UUID {
        return userRepository.insertUser(
            userInfo = userInfo,
            requiresPasswordReset = requiresPasswordReset,
            roleName = roleName,
        )
    }

    fun resetPassword(userId: UUID, newPassword: String) {
        userRepository.updatePassword(userId, newPassword)
    }

}