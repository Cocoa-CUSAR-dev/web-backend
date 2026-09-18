package com.cocoa.web.controller

import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.Sso
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.SsoService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// US2-6: mints the short-lived token behind the diary card's "view full
// history" button. Gated by X-Service-Key like DiaryServiceController --
// same trust boundary (see ServiceKeyFilter).
@RestController
@RequestMapping("/service/sso")
@Tag(
    name = "Chatbot Service",
    description = "Trusted first-party service routes, gated by X-Service-Key (see ServiceKeyFilter), not a farmer/researcher JWT",
)
class SsoServiceController(
    private val ssoService: SsoService,
) {
    @Operation(summary = "Mint a short-lived SSO token for a farmer (service-to-service)")
    @PostMapping("/tokens")
    fun mintToken(
        @RequestBody request: Sso.Request.Mint,
    ): ResponseEntity<ApiResponse<String>> {
        return ssoService.mintToken(request.userId).toResponseEntity(HttpStatus.OK)
    }
}
