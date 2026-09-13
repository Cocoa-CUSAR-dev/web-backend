package com.cocoa.web.model

import java.time.LocalDateTime
import java.util.UUID

object Task {
    data class Entity(
        val taskId: UUID,
        val title: String,
        val description: String,
        val taskType: String,
        val openAt: LocalDateTime?,
        val closeAt: LocalDateTime?,
    )
}
