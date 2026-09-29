package com.cocoa.web.client

import com.cocoa.web.model.Reminder
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.util.UUID

/** The chatbot answers in snake_case; this pins that its JSON maps onto ChatbotReminder. */
class ChatbotReminderJsonTest {
    private val mapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    @Test
    fun `maps the chatbot snake_case response`() {
        val scheduleId = UUID.randomUUID()
        val taskId = UUID.randomUUID()
        val createdBy = UUID.randomUUID()
        val roleId = UUID.randomUUID()
        val json =
            """
            {"schedule_id":"$scheduleId","task_id":"$taskId","cadence":"DAILY",
             "time_of_day":"17:00:00","is_active":false,"created_by":"$createdBy",
             "recipients":[{"type":"ROLE","id":"$roleId"}]}
            """.trimIndent()

        val parsed = mapper.readValue(json, ChatbotReminder::class.java)

        assertEquals(scheduleId, parsed.scheduleId)
        assertEquals(taskId, parsed.taskId)
        assertEquals(LocalTime.of(17, 0), parsed.timeOfDay)
        assertFalse(parsed.isActive)
        assertEquals(listOf(Reminder.Recipient(Reminder.RecipientType.ROLE, roleId)), parsed.recipients)
    }

    @Test
    fun `recipients default to empty when the chatbot omits them`() {
        val json =
            """
            {"schedule_id":"${UUID.randomUUID()}","task_id":"${UUID.randomUUID()}","cadence":"DAILY",
             "time_of_day":"09:00:00","is_active":true,"created_by":"${UUID.randomUUID()}"}
            """.trimIndent()

        assertEquals(emptyList<Reminder.Recipient>(), mapper.readValue(json, ChatbotReminder::class.java).recipients)
    }
}
