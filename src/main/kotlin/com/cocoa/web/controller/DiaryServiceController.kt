package com.cocoa.web.controller

import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.Diary
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.DiaryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// US2-6 (docs-and-plan#130, #132, #133): triggered by the chatbot right
// after its own submit_task call succeeds, so today's diary reflects the
// submission that just landed. No farmer JWT exists on this path (see
// ServiceKeyFilter's own comment) -- userId is caller-supplied and checked
// in DiaryService.generateAndPersist before anything is generated.
@RestController
@RequestMapping("/service/diaries")
@Tag(
    name = "Chatbot Service",
    description = "Trusted first-party service routes, gated by X-Service-Key (see ServiceKeyFilter), not a farmer/researcher JWT",
)
class DiaryServiceController(
    private val diaryService: DiaryService,
) {
    @Operation(
        summary = "Generate and persist today's diary (service-to-service)",
        description = "Regenerates the whole day's diary from every response submitted so far today, not just the one that just landed.",
    )
    @PostMapping("/generate")
    fun generate(
        @RequestBody request: Diary.Request.Generate,
    ): ResponseEntity<ApiResponse<String>> {
        // No explicit date here -- DiaryService.generateAndPersist's own
        // default decides "today," in the farmer's timezone, not whatever
        // the JVM host happens to be in (confirmed live 2026-09-18: a
        // Docker-hosted Go service on UTC and this service on the host's
        // Asia/Bangkok clock disagreed on which calendar day a submission
        // near midnight belonged to).
        val diaryText = diaryService.generateAndPersist(request.userId)

        return diaryText.toResponseEntity(HttpStatus.OK)
    }
}
