package com.cocoa.web.model

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

@Schema(description = "Standard API Wrapper")
data class ApiResponse<T>(
    @Schema(description = "The actual response data")
    val value: T?,
    @Schema(description = "Error message if request failed", nullable = true)
    val error: String? = null,
)

fun <T> T.toApiResponse(): ApiResponse<T> {
    return ApiResponse(
        value = this,
        error = null,
    )
}

fun <T> Exception.toApiResponse(): ApiResponse<T> {
    return ApiResponse(
        value = null,
        error = this.message,
    )
}

fun <T> T.toResponseEntity(
    httpStatus: HttpStatus
): ResponseEntity<ApiResponse<T>> {
    return ResponseEntity
        .status(httpStatus)
        .body(this.toApiResponse())
}