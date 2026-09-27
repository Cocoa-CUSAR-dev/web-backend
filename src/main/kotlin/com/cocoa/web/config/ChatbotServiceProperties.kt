package com.cocoa.web.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("chatbot-service")
data class ChatbotServiceProperties(
    // Unchanged: the key CHATBOT presents when it calls INTO this service
    // (ServiceKeyFilter / FormServiceController) -- checked, never sent.
    val key: String = "",
    // Base URL of the chatbot service -- for the new, opposite direction:
    // this service calling OUT to chatbot (ChatbotClient).
    val url: String = "",
    // The key THIS service presents on that outbound call, as
    // X-Service-Key -- must match chatbot's OWN CHATBOT_SERVICE_KEY (its
    // inbound-auth secret, see that repo's src/notifications/config.py).
    // Deliberately a separate property from `key` above despite the
    // similar name: `key` is a secret chatbot owns and this service only
    // verifies; `reminderKey` is a secret chatbot owns and this service
    // must present -- the same two services, but two unrelated secrets for
    // the two opposite directions between them.
    val reminderKey: String = "",
)
