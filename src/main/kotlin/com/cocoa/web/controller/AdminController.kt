package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.User
import com.cocoa.web.model.User.toCreate
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Administrative operations and user management")
class AdminController(
    private val userService: UserService,
) : BaseController() {
    @PreAuthorize("hasAuthority('create:user:any')")
    @Operation(summary = "Create a new user", description = "Creates a user with a mandatory password reset flag.")
    @PostMapping("/users")
    fun createUser(
        @RequestBody request: User.Request.Register,
    ): ResponseEntity<ApiResponse<String>> {
        userService.addNewUser(
            userInfo = request.toCreate(),
            requiresPasswordReset = true,
            roleName = "researcher",
        )

        return "Successfully created user".toResponseEntity(HttpStatus.CREATED)
    }
}
