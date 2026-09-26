package com.cocoa.web.service

import com.cocoa.web.config.LlmProperties
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

// US2-6 (docs-and-plan#132): calls Gemini's REST API directly to polish the
// template's wording -- see LlmProperties for why this isn't chatbot's
// LiteLLM wrapper. A failure here is never fatal to diary generation:
// DiaryService falls back to the plain template text (see its own comment)
// rather than surface an error to the farmer over what's just phrasing.
@Component
class DiaryLlmClient(
    private val llmProperties: LlmProperties,
    restClientBuilder: RestClient.Builder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val restClient = restClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build()

    // Prompt is deliberately strict: rephrase only, never add or drop a
    // fact. DiaryService's fact-guard is the actual enforcement (a prompt
    // alone is not a guarantee), this is just making the ask unambiguous.
    fun polish(templateText: String): String? {
        if (llmProperties.apiKey.isBlank()) {
            logger.warn("llm.api-key not configured -- skipping diary polish, template text will be used as-is")
            return null
        }

        val prompt =
            """
            เกลาข้อความบันทึกการเกษตรต่อไปนี้ให้เป็นภาษาไทยที่อ่านลื่นเป็นธรรมชาติ เหมือนไดอารี่ที่คนเขียนเอง
            ไม่ใช่การทวนคำถามฟอร์มแล้วตอบทีละข้อ

            กฎการเกลา:
            - ห้ามเพิ่มข้อเท็จจริงใหม่ ห้ามเปลี่ยนตัวเลขหรือชื่อเฉพาะใดๆ ที่มีอยู่ในข้อความต้นฉบับ
            - โครงประโยคใช่/ไม่ใช่ (เช่น "...หรือไม่: ใช่") ให้เขียนเป็นประโยคบอกเล่าตรงๆ แทนการทวนคำถามแล้วตอบ เช่น
              "ในประเด็นที่ว่าทำให้โกโก้เสียคุณภาพหรือไม่ ตอบว่า ใช่" -> "ทำให้โกโก้เสียคุณภาพ"
              "รดน้ำเพิ่มเติมหรือไม่ ตอบว่า ไม่" -> "ไม่ได้รดน้ำเพิ่มเติม"
            - ป้ายชื่อคำถามบางอันมีคำแนะนำการกรอกฟอร์มติดมาด้วย เช่น "กรอกเฉพาะหากเป็นการหมัก",
              "หากจัดการได้โปรดระบุ", "หากทำทั้งฟาร์มไม่ต้องระบุ" -- นี่คือคำแนะนำสำหรับคนกรอกฟอร์ม
              ไม่ใช่เหตุการณ์ที่เกิดขึ้นจริง ให้ตัดส่วนนี้ทิ้ง เหลือแต่เนื้อหาที่ตอบจริงเท่านั้น
            - ตอบกลับเป็นข้อความไดอารี่ล้วนๆ ไม่ต้องมีคำนำหรือคำอธิบายอื่น

            ข้อความต้นฉบับ:
            $templateText
            """.trimIndent()

        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val response =
                    restClient.post()
                        .uri("/v1beta/models/{model}:generateContent?key={key}", llmProperties.model, llmProperties.apiKey)
                        .body(GenerateContentRequest(contents = listOf(Content(parts = listOf(Part(text = prompt))))))
                        .retrieve()
                        .body<GenerateContentResponse>()

                return response
                    ?.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            } catch (ex: HttpServerErrorException.ServiceUnavailable) {
                // "High demand" is usually a few seconds, not a real outage
                // (confirmed live 2026-09-18) -- worth one or two short
                // retries before giving up, unlike other failure types.
                if (attempt == MAX_ATTEMPTS - 1) {
                    logger.warn("Gemini still unavailable after $MAX_ATTEMPTS attempts, falling back to template text", ex)
                    return null
                }
                logger.warn("Gemini reported 503 (high demand) -- retrying in ${RETRY_DELAY_MS}ms (attempt ${attempt + 1}/$MAX_ATTEMPTS)")
                Thread.sleep(RETRY_DELAY_MS)
            } catch (ex: Exception) {
                logger.warn("Gemini call failed, falling back to template text", ex)
                return null
            }
        }
        return null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GenerateContentRequest(val contents: List<Content>)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Content(val parts: List<Part>)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Part(val text: String)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GenerateContentResponse(val candidates: List<Candidate>? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Candidate(val content: Content? = null)

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 2000L
    }
}
