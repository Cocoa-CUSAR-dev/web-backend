package com.cocoa.web.exception

import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.toApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

open class EntityNotFoundException(
    message: String = "Entity not found"
): RuntimeException(message)

class InvalidTableCodeException(
    message: String = "Invalid table code"
): RuntimeException(message)

class LocationHierarchyException(
    message: String = "The provided location hierarchy is inconsistent"
): RuntimeException(message)

class UserNotFoundException(
    message: String = "User not found"
): EntityNotFoundException(message)

class PermissionDeniedException(
    message: String = "You do not have permission to perform this action"
) : RuntimeException(message)

class UnsupportedSheetException(
    message: String = "The requested sheet type is not supported"
) : RuntimeException(message)

class EmptyExportException(
    message: String = "No data available for the requested export"
) : RuntimeException(message)