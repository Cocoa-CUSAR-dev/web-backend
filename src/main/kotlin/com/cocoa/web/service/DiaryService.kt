package com.cocoa.web.service

import com.cocoa.web.exception.EntityNotFoundException
import com.cocoa.web.exception.PermissionDeniedException
import com.cocoa.web.model.Diary
import com.cocoa.web.repository.DiaryRepository
import com.cocoa.web.repository.FormResponseRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

// Same convention as FormResponseRepository's FARMER_ZONE -- "today" means
// the farmer's own calendar day (Asia/Bangkok), not whichever timezone this
// JVM's host happens to default to.
private val FARMER_ZONE = ZoneId.of("Asia/Bangkok")

// US2-6 (docs-and-plan#130): orchestrates resolve -> template -> LLM polish
// -> persist. The LLM is best-effort only -- a bad or unavailable polish
// falls back to the plain template text instead of failing the request.
@Service
class DiaryService(
    private val formResponseRepository: FormResponseRepository,
    private val diaryRepository: DiaryRepository,
    private val diaryAnswerResolver: DiaryAnswerResolver,
    private val diaryLlmClient: DiaryLlmClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getByDate(
        userId: UUID,
        date: LocalDate,
    ): Diary.Entity {
        return diaryRepository.findByUserAndDate(userId, date)
            ?: throw EntityNotFoundException("No diary entry for $date")
    }

    // Called right after the chatbot's submit_task succeeds. `userId` is
    // caller-supplied since there's no farmer JWT on that path --
    // hasChatConversation is the ownership check that stops a
    // buggy/compromised caller from generating another farmer's diary from
    // a made-up id (same precedent as mobile-backend's SubmitTaskForUser).
    fun generateAndPersist(
        userId: UUID,
        date: LocalDate = LocalDate.now(FARMER_ZONE),
    ): String {
        if (!diaryRepository.hasChatConversation(userId)) {
            throw PermissionDeniedException("No chatbot conversation found for this user")
        }

        val answerFields = formResponseRepository.fetchAnswerFieldsForUserAndDate(userId, date)
        if (answerFields.isEmpty()) {
            throw EntityNotFoundException("No submissions on $date to generate a diary from")
        }

        val resolvedFields = diaryAnswerResolver.resolveAll(answerFields)
        val templateText = DiaryTemplateRenderer.render(resolvedFields)

        val polished = diaryLlmClient.polish(templateText)
        val diaryText =
            when {
                polished == null -> {
                    logger.warn("LLM polish returned no usable text for user_id={}, date={} -- using template", userId, date)
                    templateText
                }
                !DiaryFactGuard.preservesFacts(resolvedFields, polished) -> {
                    logger.warn(
                        "LLM polish output failed the fact-guard for user_id={}, date={} -- using template. " +
                            "template={} | polished={}",
                        userId,
                        date,
                        templateText,
                        polished,
                    )
                    templateText
                }
                else -> polished
            }

        diaryRepository.upsert(userId, date, diaryText)
        return diaryText
    }
}
