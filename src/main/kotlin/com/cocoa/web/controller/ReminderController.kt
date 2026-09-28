package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.Reminder
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.ReminderService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Same permission as creating a form (create:form:all) on purpose: whoever
 * can create a form can also manage its reminder, so no separate permission.
 */
@RestController
@RequestMapping
class ReminderController(
    private val reminderService: ReminderService,
) : BaseController() {
    @PreAuthorize("hasAuthority('create:form:all')")
    @Operation(summary = "Get the reminder settings of a form's task")
    @GetMapping("/forms/{formId}/reminder")
    fun getReminder(
        @PathVariable formId: UUID,
    ): ResponseEntity<ApiResponse<Reminder.Detail>> = reminderService.getReminder(formId).toResponseEntity(HttpStatus.OK)

    @PreAuthorize("hasAuthority('create:form:all')")
    @Operation(
        summary = "Create or update the reminder of a form's task",
        description = "enabled=false switches it off (time and recipients are kept). Empty recipients = everyone who still owes the task.",
    )
    @PutMapping("/forms/{formId}/reminder")
    fun saveReminder(
        @PathVariable formId: UUID,
        @RequestBody request: Reminder.Request.Create,
    ): ResponseEntity<ApiResponse<Reminder.Detail>> =
        reminderService.saveReminder(formId, request, getAuthenticatedUser().userId)
            .toResponseEntity(HttpStatus.OK)

    @PreAuthorize("hasAuthority('create:form:all')")
    @Operation(summary = "Roles that can be picked as reminder recipients")
    @GetMapping("/reminders/recipient-options/roles")
    fun getRoleOptions(): ResponseEntity<ApiResponse<List<Reminder.RoleOption>>> =
        reminderService.getRoleOptions().toResponseEntity(
            HttpStatus.OK,
        )

    @PreAuthorize("hasAuthority('create:form:all')")
    @Operation(summary = "Search users that can be picked as individual reminder recipients (max 50)")
    @GetMapping("/reminders/recipient-options/users")
    fun searchUserOptions(
        @RequestParam(defaultValue = "") q: String,
    ): ResponseEntity<ApiResponse<List<Reminder.UserOption>>> = reminderService.searchUserOptions(q).toResponseEntity(HttpStatus.OK)
}
