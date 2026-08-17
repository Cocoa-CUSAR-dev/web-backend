package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.Form
import com.cocoa.web.model.Handler
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.FormService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/forms")
class FormController(
    private val formService: FormService,
) : BaseController() {
    @PreAuthorize("hasAuthority('read:form:all') or hasAuthority('read:form:assigned')")
    @Operation(summary = "Get specific form structure")
    @GetMapping("/{formId}")
    fun getForm(
        @PathVariable formId: UUID,
    ): ResponseEntity<ApiResponse<Form.Detail>> {
        val formDetail = formService.getFormDetail(formId)

        return formDetail.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:form:all')")
    @Operation(summary = "List all available forms")
    @GetMapping
    fun getForms(): ResponseEntity<ApiResponse<List<Form.Entity>>> {
        val forms = formService.getForms()

        return forms.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('update:form:all')")
    @Operation(summary = "Update form configuration")
    @PutMapping("/{formId}/edit")
    fun formEdit(
        @PathVariable formId: UUID,
        @RequestBody request: Form.Request.Edit,
    ): ResponseEntity<ApiResponse<String>> {
        formService.editForm(formId, request)

        return "Successfully audited form".toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('create:form:all')")
    @Operation(summary = "List valid handler values for form authoring")
    @GetMapping("/handlers")
    fun getHandlers(): ResponseEntity<ApiResponse<List<String>>> {
        val handlers = formService.getHandlers()

        return handlers.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('create:form:all')")
    @Operation(summary = "List a handler's destination table columns, for the form builder's field_name dropdown")
    @GetMapping("/handlers/{handler}/fields")
    fun getHandlerFields(
        @PathVariable handler: String,
    ): ResponseEntity<ApiResponse<List<Handler.Field>>> {
        val fields = formService.getHandlerFields(handler)

        return fields.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('update:form:all')")
    @Operation(
        summary = "Fully update a form",
        description = "Writes label/inputType/fieldName/sortOrder/description and supports adding/removing sections and questions.",
    )
    @PutMapping("/{formId}")
    fun updateForm(
        @PathVariable formId: UUID,
        @RequestBody request: Form.Request.Update,
    ): ResponseEntity<ApiResponse<Form.Detail>> {
        val form = formService.updateForm(formId, request)

        return form.toResponseEntity(HttpStatus.OK)
    }
}
