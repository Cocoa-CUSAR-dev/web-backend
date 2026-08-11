package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.Form
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.FormService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/service/forms")
@Tag(
    name = "Chatbot Service",
    description = "Trusted first-party service routes, gated by X-Service-Key (see ServiceKeyFilter), not a farmer/researcher JWT",
)
class FormServiceController(
    private val formService: FormService,
) : BaseController() {
    @Operation(
        summary = "Get specific form structure (service-to-service)",
        description =
            "Same data as GET /forms/{formId}, for callers with no farmer session to forward a JWT from " +
                "(e.g. the chatbot). Exposes form structure only, never a farmer's own answers, so no " +
                "per-caller ownership check is needed here.",
    )
    @GetMapping("/{formId}")
    fun getForm(
        @PathVariable formId: UUID,
    ): ResponseEntity<ApiResponse<Form.Detail>> {
        val formDetail = formService.getFormDetail(formId)

        return formDetail.toResponseEntity(HttpStatus.OK)
    }
}
