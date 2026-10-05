package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.base.PageRequest
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.ResponseReview
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.ResponseReviewService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// US2-8 (docs-and-plan#171/#172). Kept apart from FormResponseController:
// that one is the read-only pivot-by-question view, this one is per
// submission and is the only place a response can be written to.
@RestController
@RequestMapping("/tasks/{taskId}/review")
@Tag(name = "Response Review", description = "Researcher review and correction of chatbot-submitted answers")
class ResponseReviewController(
    private val responseReviewService: ResponseReviewService,
) : BaseController() {
    @PreAuthorize("hasAuthority('read:response:all')")
    @Operation(
        summary = "List a task's submissions field by field",
        description = "Newest first. aiOnly=true keeps only farmers who have at least one AI-extracted answer for this task.",
    )
    @GetMapping
    fun getReview(
        @PathVariable taskId: UUID,
        @RequestParam(defaultValue = "false") aiOnly: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): ResponseEntity<ApiResponse<List<ResponseReview.Submission>>> {
        val submissions = responseReviewService.getReview(taskId, aiOnly, PageRequest(page, size))

        return submissions.toResponseEntity(HttpStatus.OK)
    }
}
