package com.cocoa.web.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("chatbot-service")
data class ChatbotServiceProperties(
    val key: String = "",
)
