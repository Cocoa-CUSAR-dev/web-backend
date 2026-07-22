package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.exception.PermissionDeniedException
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.Researcher
import com.cocoa.web.model.Researcher.toCreate
import com.cocoa.web.model.Researcher.toUpdate
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.ResearcherService
import com.cocoa.web.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/researchers")
@Tag(name = "Researchers", description = "Researcher profile and organization management")
class ResearcherController(
    private val researcherService: ResearcherService,
    private val userService: UserService,
) : BaseController() {

    @PreAuthorize("hasAuthority('create:researcher:own')")
    @Operation(summary = "Register as researcher", description = "Links researcher profile to the authenticated user.")
    @PostMapping("/register")
    fun registerResearcher(
        @RequestBody request: Researcher.Request.Post,
    ): ResponseEntity<ApiResponse<String>> {
        val currentUser = getAuthenticatedUser()
        researcherService.addResearcher(currentUser.userId, request.toCreate())
        return "Successfully registered as researcher".toResponseEntity(HttpStatus.CREATED)
    }

    @PreAuthorize("hasAuthority('read:researcher:all')")
    @Operation(summary = "List researchers", description = "Search researchers with filters.")
    @GetMapping
    fun getResearchers(
        @ParameterObject filter: Researcher.Query.Filter,
    ): ResponseEntity<ApiResponse<List<Researcher.Entity>>> {
        val researchers = researcherService.getResearchers(filter)
        return researchers.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:researcher:own')")
    @Operation(summary = "Get current researcher info")
    @GetMapping("/me")
    fun getSelfResearcherInfo(): ResponseEntity<ApiResponse<Researcher.Detail>> {
        val currentUser = getAuthenticatedUser()
        val userDetail = userService.getUserDetail(currentUser.userId)
        val researcher = researcherService.getResearcher(currentUser.userId)
        val researcherInfo = Researcher.Detail(
            email = userDetail.email,
            firstName = researcher.firstName,
            lastName = researcher.lastName,
            organization = researcher.organization,
            isPasswordReset = userDetail.isPasswordReset,
            isRequiresPasswordReset = userDetail.isRequiresPasswordReset,
            roles = userDetail.roles
        )
        return researcherInfo.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:researcher:all')")
    @Operation(summary = "Get researcher by ID")
    @GetMapping("/{userId}")
    fun getResearcher(@PathVariable userId: UUID): ResponseEntity<ApiResponse<Researcher.Entity>> {
        val researcher = researcherService.getResearcher(userId)
        return researcher.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('update:researcher:own')")
    @Operation(summary = "Update researcher profile")
    @PatchMapping("/{userId}")
    fun updateResearcher(
        @PathVariable userId: UUID,
        @RequestBody request: Researcher.Request.Patch,
    ): ResponseEntity<ApiResponse<String>> {
        val currentUser = getAuthenticatedUser()

        if (currentUser.userId != userId) {
            throw PermissionDeniedException("You can only update your own profile.")
        }

        researcherService.updateResearcher(userId, request.toUpdate())
        return "Successfully updated researcher".toResponseEntity(HttpStatus.OK )
    }

    @PreAuthorize("hasAuthority('delete:researcher:any')")
    @Operation(summary = "Delete researcher profile")
    @DeleteMapping("/{userId}")
    fun deleteResearcher(@PathVariable userId: UUID): ResponseEntity<ApiResponse<String>> {
        researcherService.deleteResearcher(userId)
        return "Successfully deleted researcher".toResponseEntity(HttpStatus.OK)
    }
}