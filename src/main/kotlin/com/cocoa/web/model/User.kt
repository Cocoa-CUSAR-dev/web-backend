package com.cocoa.web.model

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.UUID

object User {
    data class Entity(
        val userId: UUID,
        val username: String,
        val passwordHash: String,
        val isPasswordReset: Boolean,
        val roles: List<String>,
        val permissions: List<String>,
        val createdAt: LocalDateTime,
        val updatedAt: LocalDateTime,
    )

    data class Detail(
        val userId: UUID,
        val email: String,
        val firstName: String?,
        val lastName: String?,
        val organization: String?,
        val isPasswordReset: Boolean,
        val isRequiresPasswordReset: Boolean,
        val roles: List<String>,
    )

    object Input {
        data class Create(
            val username: String,
            val password: String,
        )
    }

    object Query {
        data class Filter(
            val username: String? = null,
        )
    }

    object Request {
        data class Register(
            @Schema(required = true, example = "user@example.com")
            val username: String,
            @Schema(required = true, example = "password123")
            val password: String,
        )

        data class Login(
            @Schema(required = true)
            val username: String,
            @Schema(required = true)
            val password: String,
        )

        data class ResetPassword(
            @Schema(required = true)
            val newPassword: String,
        )
    }

    fun Request.Register.toCreate(): Input.Create {
        return Input.Create(
            username = this.username,
            password = this.password,
        )
    }
}
