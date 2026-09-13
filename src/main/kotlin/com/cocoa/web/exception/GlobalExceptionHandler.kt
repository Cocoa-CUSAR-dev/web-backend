package com.cocoa.web.exception

import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.toApiResponse
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleNotFound(ex: EntityNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ex.toApiResponse<Unit>())

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ex.toApiResponse<Unit>())

    @ExceptionHandler(BadCredentialsException::class)
    fun handleUnauthorized(ex: BadCredentialsException) =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ex.toApiResponse<Unit>())

    @ExceptionHandler(PermissionDeniedException::class)
    fun handlePermissionDenied(ex: PermissionDeniedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ex.toApiResponse<Unit>())

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun handleSpringSecurityDenied(ex: org.springframework.security.access.AccessDeniedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body("Access Denied: You do not have the required permissions.".toApiResponse())

    @ExceptionHandler(DuplicateKeyException::class)
    fun handleConflict(ex: DuplicateKeyException) =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ex.toApiResponse<Unit>())

    @ExceptionHandler(InvalidTableCodeException::class)
    fun handleInvalidTableCode(ex: InvalidTableCodeException) =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ex.toApiResponse<Unit>())

    @ExceptionHandler(LocationHierarchyException::class)
    fun handleLocationMismatch(ex: LocationHierarchyException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ex.toApiResponse<Unit>())

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        ex: org.springframework.web.method.annotation.MethodArgumentTypeMismatchException,
    ): ResponseEntity<ApiResponse<String>> {
        val message = "Invalid parameter value: ${ex.value}. Expected one of the supported types."
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse(value = null, error = message))
    }

    @ExceptionHandler(UnsupportedSheetException::class)
    fun handleUnsupportedSheet(ex: UnsupportedSheetException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY) // 422 is great for "valid syntax, but wrong logic"
            .body(ex.toApiResponse<Unit>())

    @ExceptionHandler(EmptyExportException::class)
    fun handleEmptyExport(ex: EmptyExportException) =
        ResponseEntity.status(HttpStatus.NO_CONTENT) // 204 No Content
            .body(ex.toApiResponse<Unit>())

    // BE-7: this is the catch-all for anything not already mapped above --
    // a JDBC/jOOQ failure, a NullPointerException, anything unanticipated.
    // Previously ex.message went straight to the client (could leak SQL/
    // schema details) and nothing was logged server-side, so an incident
    // like this was both invisible to us and informative to the caller --
    // backwards. Typed handlers above (404/400/403/etc.) keep echoing
    // their message since those are deliberate, user-facing validation
    // errors, not internals leakage.
    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ApiResponse<Unit>> {
        logger.error("Unhandled exception", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse(value = null, error = "Internal server error"))
    }
}
