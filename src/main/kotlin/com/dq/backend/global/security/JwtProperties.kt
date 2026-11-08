package com.dq.backend.global.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "dq.jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenExpirySeconds: Long,
    val refreshTokenExpirySeconds: Long,
)
