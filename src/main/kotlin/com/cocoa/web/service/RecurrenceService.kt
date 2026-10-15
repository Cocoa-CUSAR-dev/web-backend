package com.cocoa.web.service

import com.cocoa.web.base.BaseService
import com.cocoa.web.client.ChatbotClient
import com.cocoa.web.client.ChatbotReminder
import com.cocoa.web.exception.ChatbotUnavailableException
import com.cocoa.web.model.Recurrence
import com.cocoa.web.repository.FormResponseRepository
import com.cocoa.web.repository.RecurrenceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * US4-4 (docs-and-plan#233): opens the next round of every repeating task.
 *
 * Run from outside on a schedule (POST /service/recurrence/run, called by a
 * GitHub Actions cron) rather than by an in-process timer, for the same reason
 * the chatbot's cron jobs are: a timer inside the process is not dependable on
 * a host that can sleep.
 *
 * Safe to run any number of times, and to run late: which rounds exist is
 * decided by the database (UNIQUE recurrence + day), never by what an earlier
 * run remembers. A run that finds nothing due does nothing.
 */
@Service
class RecurrenceService(
    private val recurrenceRepository: RecurrenceRepository,
    private val chatbotClient: ChatbotClient,
) : BaseService() {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun generateDueRounds(now: LocalDateTime = LocalDateTime.now(FormResponseRepository.FARMER_ZONE)): Recurrence.RunResult {
        val today = now.toLocalDate()
        var created = 0
        var alreadyExisted = 0
        var skippedClosed = 0
        var skippedNoRecipients = 0
        var failed = 0

        val running = recurrenceRepository.fetchRunning(today.minusDays(RecurrenceSchedule.LOOKBACK_DAYS))
        for (recurrence in running) {
            val templateOpenAt = recurrence.templateOpenAt
            val templateCloseAt = recurrence.templateCloseAt
            if (templateOpenAt == null || templateCloseAt == null) {
                logger.error("recurrence {} skipped: its template task has no open/close time", recurrence.recurrenceId)
                failed++
                continue
            }

            val dueDates = RecurrenceSchedule.dueDates(recurrence, today)
            if (dueDates.isEmpty()) continue

            val assignees: List<UUID>
            val existing: Set<LocalDate>
            try {
                val rules = recurrenceRepository.fetchRecipients(recurrence.templateTaskId)
                assignees = if (rules.isEmpty()) emptyList() else recurrenceRepository.resolveUserIds(rules)
                if (rules.isNotEmpty() && assignees.isEmpty()) {
                    // Opening the round anyway would show it to everybody -- the
                    // opposite of what the recipients were meant to do.
                    logger.warn("recurrence {} skipped: its recipients no longer match any user", recurrence.recurrenceId)
                    skippedNoRecipients += dueDates.size
                    continue
                }
                existing = recurrenceRepository.fetchExistingPeriods(recurrence.recurrenceId, dueDates)
            } catch (e: Exception) {
                logger.error("recurrence {} skipped: could not read its recipients or rounds", recurrence.recurrenceId, e)
                failed++
                continue
            }

            // Fetched once per recurrence, and only when a round is really opened.
            var templateReminders: List<ChatbotReminder>? = null

            for (date in dueDates) {
                if (date in existing) {
                    alreadyExisted++
                    continue
                }
                val (openAt, closeAt) = RecurrenceSchedule.window(templateOpenAt, templateCloseAt, date)
                if (!closeAt.isAfter(now)) {
                    // Over before it could be opened (the job was down) -- nobody can use it.
                    skippedClosed++
                    continue
                }

                val newTaskId =
                    try {
                        recurrenceRepository.createRound(recurrence, date, openAt, closeAt, assignees)
                    } catch (e: Exception) {
                        logger.error("recurrence {} round for {} failed", recurrence.recurrenceId, date, e)
                        failed++
                        continue
                    }
                if (newTaskId == null) {
                    // Another run created it between our check and our insert.
                    alreadyExisted++
                    continue
                }

                created++
                logger.info("recurrence {} opened round for {} as task {}", recurrence.recurrenceId, date, newTaskId)

                if (templateReminders == null) templateReminders = loadTemplateReminders(recurrence)
                copyReminders(templateReminders, newTaskId, recurrence)
            }
        }

        val result = Recurrence.RunResult(created, alreadyExisted, skippedClosed, skippedNoRecipients, failed)
        logger.info("recurrence run finished: {}", result)
        return result
    }

    // A reminder belongs to one task, so without this every new round would
    // start with none. The chatbot owns the reminder tables, so the template's
    // schedules are read from it and recreated for the round. Best effort, like
    // creating a reminder with a form: the round is already open and a missing
    // reminder must not undo it (or stop the other rounds).
    private fun loadTemplateReminders(recurrence: Recurrence.Active): List<ChatbotReminder> =
        try {
            chatbotClient.listReminderSchedules(recurrence.templateTaskId).filter { it.isActive }
        } catch (e: ChatbotUnavailableException) {
            logger.warn("reminders of recurrence {} not copied (chatbot unavailable): {}", recurrence.recurrenceId, e.message)
            emptyList()
        }

    private fun copyReminders(
        reminders: List<ChatbotReminder>,
        newTaskId: UUID,
        recurrence: Recurrence.Active,
    ) {
        reminders.forEach { reminder ->
            try {
                chatbotClient.createReminderSchedule(newTaskId, reminder.timeOfDay, recurrence.createdBy, reminder.recipients)
            } catch (e: ChatbotUnavailableException) {
                logger.warn("reminder for task {} (round of {}) not created: {}", newTaskId, recurrence.recurrenceId, e.message)
            }
        }
    }
}
