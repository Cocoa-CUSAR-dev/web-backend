package com.cocoa.web.client

import com.cocoa.web.config.ChatbotServiceProperties
import com.cocoa.web.exception.ChatbotUnavailableException
import com.cocoa.web.model.Reminder
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.LocalTime
import java.util.UUID

/** The chatbot's reminder-schedule JSON (snake_case on the wire). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ChatbotReminder(
    val scheduleId: UUID,
    val taskId: UUID,
    val cadence: String,
    val timeOfDay: LocalTime,
    @get:JsonProperty("is_active")
    @param:JsonProperty("is_active")
    val isActive: Boolean,
    val createdBy: UUID,
    val recipients: List<Reminder.Recipient> = emptyList(),
)

/**
 * Outbound calls to the chatbot service -- the reverse direction of
 * ServiceKeyFilter/FormServiceController, which is chatbot calling INTO
 * this service. The chatbot is the only writer of notify.reminder_schedule
 * and notify.reminder_recipient; this service only ever asks it to.
 *
 * Every method throws ChatbotUnavailableException on any failure (chatbot
 * down, rejected the request, ...). Callers that treat a reminder as a
 * secondary effect (FormService.createForm) catch it; the reminder settings
 * endpoints let it surface as a 502.
 */
@Component
class ChatbotClient(
    private val properties: ChatbotServiceProperties,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val restClient = RestClient.create()

    private fun recipientsBody(recipients: List<Reminder.Recipient>) =
        recipients.map { mapOf("type" to it.type.name, "id" to it.id.toString()) }

    private fun <T> call(
        what: String,
        block: () -> T,
    ): T {
        try {
            return block()
        } catch (e: RestClientException) {
            logger.error("chatbot call failed: $what", e)
            throw ChatbotUnavailableException("Reminder service is unavailable ($what)")
        }
    }

    fun createReminderSchedule(
        taskId: UUID,
        timeOfDay: LocalTime,
        createdBy: UUID,
        recipients: List<Reminder.Recipient> = emptyList(),
    ): ChatbotReminder =
        call("create schedule for task_id=$taskId") {
            restClient.post()
                .uri("${properties.url}/service/reminders")
                .header("X-Service-Key", properties.reminderKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "task_id" to taskId.toString(),
                        "time_of_day" to timeOfDay.toString(),
                        "created_by" to createdBy.toString(),
                        "recipients" to recipientsBody(recipients),
                    ),
                )
                .retrieve()
                .body(ChatbotReminder::class.java)!!
        }

    fun listReminderSchedules(taskId: UUID): List<ChatbotReminder> =
        call("list schedules for task_id=$taskId") {
            restClient.get()
                .uri("${properties.url}/service/reminders?task_id={taskId}", taskId)
                .header("X-Service-Key", properties.reminderKey)
                .retrieve()
                .body(object : ParameterizedTypeReference<List<ChatbotReminder>>() {})
                ?: emptyList()
        }

    /** Only the fields passed are changed; `recipients` replaces the whole list (empty = everyone). */
    fun updateReminderSchedule(
        scheduleId: UUID,
        timeOfDay: LocalTime? = null,
        isActive: Boolean? = null,
        recipients: List<Reminder.Recipient>? = null,
    ): ChatbotReminder =
        call("update schedule_id=$scheduleId") {
            val body = mutableMapOf<String, Any>()
            timeOfDay?.let { body["time_of_day"] = it.toString() }
            isActive?.let { body["is_active"] = it }
            recipients?.let { body["recipients"] = recipientsBody(it) }
            restClient.patch()
                .uri("${properties.url}/service/reminders/{id}", scheduleId)
                .header("X-Service-Key", properties.reminderKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ChatbotReminder::class.java)!!
        }
}
