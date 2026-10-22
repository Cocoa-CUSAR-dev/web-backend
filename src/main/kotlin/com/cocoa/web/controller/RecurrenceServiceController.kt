package com.cocoa.web.controller

import com.cocoa.web.base.BaseController
import com.cocoa.web.model.ApiResponse
import com.cocoa.web.model.Recurrence
import com.cocoa.web.model.toResponseEntity
import com.cocoa.web.service.RecurrenceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/service/recurrence")
@Tag(
    name = "Chatbot Service",
    description = "Trusted first-party service routes, gated by X-Service-Key (see ServiceKeyFilter), not a farmer/researcher JWT",
)
class RecurrenceServiceController(
    private val recurrenceService: RecurrenceService,
) : BaseController() {
    @Operation(
        summary = "Open the next round of every repeating task",
        description =
            "Called on a schedule by cron (see .github/workflows/cron-recurring-tasks.yml). Safe to call as often " +
                "as you like: a round that already exists is never created twice. Answers 500 with the counts if " +
                "any round failed, so a failing run is visible to whoever triggered it.",
    )
    @PostMapping("/run")
    fun run(): ResponseEntity<ApiResponse<Recurrence.RunResult>> {
        val result = recurrenceService.generateDueRounds()

        return result.toResponseEntity(if (result.failed > 0) HttpStatus.INTERNAL_SERVER_ERROR else HttpStatus.OK)
    }
}
