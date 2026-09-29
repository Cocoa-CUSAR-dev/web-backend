package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.Diary
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.FormResponseService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

// US2-6/US5-1 (docs-and-plan#130, #134): the farmer's own submission
// history -- new capability, not a variant of FormResponseController
// (which is researcher-scoped via read:response:all and bound to one
// task). Backs web-app's history page: the day list, and the raw
// fallback view for a day with no generated diary yet.
@RestController
@RequestMapping("/me/history")
@Tag(name = "Submission History", description = "The authenticated farmer's own past form submissions")
class HistoryController(
    private val formResponseService: FormResponseService,
) : BaseController() {
    @PreAuthorize("hasAuthority('read:response:own')")
    @Operation(summary = "List days with at least one submission", description = "Newest first.")
    @GetMapping
    fun getSubmissionDays(): ResponseEntity<ApiResponse<List<LocalDate>>> {
        val userId = getAuthenticatedUser().userId
        val days = formResponseService.getOwnSubmissionDays(userId)

        return days.toResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize("hasAuthority('read:response:own')")
    @Operation(summary = "Raw answers submitted on a given day", description = "Fallback view for a day with no diary entry yet.")
    @GetMapping("/{date}")
    fun getSubmissionsForDate(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): ResponseEntity<ApiResponse<List<Diary.AnswerField>>> {
        val userId = getAuthenticatedUser().userId
        val answers = formResponseService.getOwnAnswersForDate(userId, date)

        return answers.toResponseEntity(HttpStatus.OK)
    }
}
