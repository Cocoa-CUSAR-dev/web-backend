package com.cocoa.web.client

import com.cocoa.web.config.ChatbotServiceProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.LocalTime
import java.util.UUID

/**
 * Outbound calls to the chatbot service -- the reverse direction of
 * ServiceKeyFilter/FormServiceController, which is chatbot calling INTO
 * this service. This is the first client going the other way.
 */
@Component
class ChatbotClient(
    private val properties: ChatbotServiceProperties,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val restClient = RestClient.create()

    /**
     * POST /service/reminders on the chatbot service. Best-effort: any
     * failure (chatbot unreachable, rejected the request, ...) is logged
     * and swallowed here, never thrown -- creating the reminder is a
     * secondary effect of creating a form (FormService.createForm), and a
     * chatbot outage must not fail or roll back the form itself, which is
     * the actual thing being created.
     */
    fun createReminderSchedule(
        taskId: UUID,
        timeOfDay: LocalTime,
        createdBy: UUID,
    ) {
        try {
            restClient.post()
                .uri("${properties.url}/service/reminders")
                .header("X-Service-Key", properties.reminderKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "task_id" to taskId.toString(),
                        "time_of_day" to timeOfDay.toString(),
                        "created_by" to createdBy.toString(),
                    ),
                )
                .retrieve()
                .toBodilessEntity()
        } catch (e: RestClientException) {
            logger.error("failed to create a reminder schedule on the chatbot service for task_id=$taskId", e)
        }
    }
}
