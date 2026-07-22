package com.cocoa.web.model

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

object Researcher {

    data class Entity(
        val userId: UUID,
        val firstName: String,
        val lastName: String,
        val organization: String,
    )

    data class Detail(
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
            val firstName: String,
            val lastName: String,
            val organization: String,
        )

        data class Update(
            val firstName: String? = null,
            val lastName: String? = null,
            val organization: String? = null,
        )
    }

    object Query {
        data class Filter(
            val firstName: String? = null,
            val lastName: String? = null,
            val organization: String? = null,
        )
    }

    object Request {
        data class Post(
            @Schema(required = true, example = "John")
            val firstName: String,
            @Schema(required = true, example = "Doe")
            val lastName: String,
            @Schema(required = true, example = "Cacao Research Inst.")
            val organization: String,
        )

        data class Patch(
            val firstName: String? = null,
            val lastName: String? = null,
            val organization: String? = null,
        )
    }

    fun Request.Post.toCreate(): Input.Create {
        return Input.Create(
            firstName = this.firstName,
            lastName = this.lastName,
            organization = this.organization,
        )
    }

    fun Request.Patch.toUpdate(): Input.Update {
        return Input.Update(
            firstName = this.firstName,
            lastName = this.lastName,
            organization = this.organization,
        )
    }
}