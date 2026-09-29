package com.cocoa.web.config

import org.springframework.boot.context.properties.ConfigurationProperties

// US2-6 (docs-and-plan#132): Gemini via Google AI Studio, called directly --
// unlike chatbot's LiteLLM wrapper (ADR 0004), this service has no existing
// LLM client to reuse, so it's a small first-party one against Gemini's
// REST API. model/apiKey naming mirrors chatbot's LLM_MODEL/LLM_API_KEY so
// the two services read as the same concept.
@ConfigurationProperties("llm")
data class LlmProperties(
    val model: String = "gemini-3.6-flash",
    val apiKey: String = "",
)
