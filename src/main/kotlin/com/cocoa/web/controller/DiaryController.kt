package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.Diary
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.DiaryService
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

// US2-6 (docs-and-plan#130, #132): the farmer-facing read side. Only ever
// reads what generateAndPersist already stored (see
// DiaryServiceController) -- no LLM call happens on this path, so repeat
// views are free and the wording never drifts between reads.
@RestController
@RequestMapping("/me/diaries")
@Tag(name = "Diary", description = "The authenticated farmer's own generated daily diary")
class DiaryController(
    private val diaryService: DiaryService,
) : BaseController() {
    @PreAuthorize("hasAuthority('read:response:own')")
    @Operation(
        summary = "Get the diary entry for a day",
        description =
            "404 if no diary was generated for that day (e.g. a date before this feature existed) -- " +
                "callers fall back to the raw submission list.",
    )
    @GetMapping("/{date}")
    fun getDiary(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): ResponseEntity<ApiResponse<Diary.Entity>> {
        val userId = getAuthenticatedUser().userId
        val diary = diaryService.getByDate(userId, date)

        return diary.toResponseEntity(HttpStatus.OK)
    }
}
