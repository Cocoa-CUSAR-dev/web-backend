package com.cocoa.web.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jwt")
data class JwtProperties(
    val key: String,
    val name: String,
    val accessTokenExpiration: Long,
    val refreshTokenExpiration: Long,
)
