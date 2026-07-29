package com.cocoa.web.controller

import com.cocoa.web.base.BaseService
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.FormResponse
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.FormResponseService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/tasks/{taskId}/responses")
class FormResponseController(
    private val formResponseService: FormResponseService,
) : BaseService() {
    @GetMapping
    fun getResponses(
        @PathVariable taskId: UUID,
    ): ResponseEntity<ApiResponse<List<FormResponse.Detail>>> {
        val formResponses = formResponseService.getUserResponse(taskId)

        return formResponses.toResponseEntity(HttpStatus.OK)
    }

    @GetMapping("/{responseId}")
    fun getResponse(
        @PathVariable taskId: UUID,
        @PathVariable responseId: UUID,
    ): ResponseEntity<ApiResponse<FormResponse.Entity>> {
        val formResponse =
            formResponseService.getFormResponse(
                taskId = taskId,
                formResponseId = responseId,
            )

        return formResponse.toResponseEntity(HttpStatus.OK)
    }
}
