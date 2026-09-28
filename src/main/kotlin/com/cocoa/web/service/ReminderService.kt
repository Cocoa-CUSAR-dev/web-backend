package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.client.ChatbotClient
import com.cocoa.web.client.ChatbotReminder
import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.model.Reminder
import com.cocoa.web.repository.FormRepository
import com.cocoa.web.repository.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Reminder settings for a form's task. The chatbot owns the data (it is the
 * only writer of notify.reminder_*); this service translates a form id to its
 * task id, asks the chatbot, and attaches readable names (role name /
 * username) to the recipient ids for the UI.
 */
@Service
class ReminderService(
    private val formRepository: FormRepository,
    private val userRepository: UserRepository,
    private val chatbotClient: ChatbotClient,
) : BaseService() {
    private fun taskIdOf(formId: UUID): UUID =
        formRepository.findByFormId(formId)?.taskId
            ?: throw EntityNotFoundException("Form not found")

    // A task normally has at most one schedule (the create-form page makes
    // one). If there are several, prefer an active one so the screen shows
    // the reminder that is actually running.
    private fun pick(schedules: List<ChatbotReminder>): ChatbotReminder? = schedules.firstOrNull { it.isActive } ?: schedules.firstOrNull()

    fun getReminder(formId: UUID): Reminder.Detail {
        val schedule = pick(chatbotClient.listReminderSchedules(taskIdOf(formId)))
        return toDetail(schedule)
    }

    fun saveReminder(
        formId: UUID,
        request: Reminder.Request.Create,
        actingUserId: UUID,
    ): Reminder.Detail {
        require(request.recipients.size <= MAX_RECIPIENTS) { "Too many recipients (max $MAX_RECIPIENTS)" }

        val taskId = taskIdOf(formId)
        val existing = pick(chatbotClient.listReminderSchedules(taskId))

        val saved =
            when {
                request.enabled && existing == null ->
                    chatbotClient.createReminderSchedule(taskId, request.timeOfDay, actingUserId, request.recipients)
                request.enabled && existing != null ->
                    chatbotClient.updateReminderSchedule(
                        existing.scheduleId,
                        timeOfDay = request.timeOfDay,
                        isActive = true,
                        recipients = request.recipients,
                    )
                // Turning off keeps the saved time and recipients, so switching it back on later restores them.
                existing != null -> chatbotClient.updateReminderSchedule(existing.scheduleId, isActive = false)
                else -> null
            }
        return toDetail(saved)
    }

    fun getRoleOptions(): List<Reminder.RoleOption> = userRepository.fetchRoleOptions()

    fun searchUserOptions(query: String): List<Reminder.UserOption> = userRepository.searchUserOptions(query, USER_SEARCH_LIMIT)

    private fun toDetail(schedule: ChatbotReminder?): Reminder.Detail {
        if (schedule == null) {
            return Reminder.Detail(scheduleId = null, enabled = false, timeOfDay = null, recipients = emptyList())
        }
        val roleNames =
            userRepository.fetchRoleNames(
                schedule.recipients.filter { it.type == Reminder.RecipientType.ROLE }.map { it.id },
            )
        val usernames =
            userRepository.fetchUsernames(
                schedule.recipients.filter { it.type == Reminder.RecipientType.USER }.map { it.id },
            )
        return Reminder.Detail(
            scheduleId = schedule.scheduleId,
            enabled = schedule.isActive,
            timeOfDay = schedule.timeOfDay,
            recipients =
                schedule.recipients.map {
                    val label =
                        when (it.type) {
                            Reminder.RecipientType.ROLE -> roleNames[it.id]
                            Reminder.RecipientType.USER -> usernames[it.id]
                        }
                    Reminder.RecipientDetail(it.type, it.id, label ?: it.id.toString())
                },
        )
    }

    companion object {
        private const val MAX_RECIPIENTS = 200
        private const val USER_SEARCH_LIMIT = 50
    }
}
