package com.cocoa.web.base

import com.cocoa.web.exception.EntityNotFoundException

abstract class BaseService {
    protected fun Int.orThrowNotFound(entityName: String): Int {
        return takeIf { it > 0 } ?: throw EntityNotFoundException("$entityName not found or do not have access")
    }
}
