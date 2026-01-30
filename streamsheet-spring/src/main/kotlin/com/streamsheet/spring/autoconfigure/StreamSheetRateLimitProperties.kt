package com.streamsheet.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "streamsheet.rate-limit")
data class StreamSheetRateLimitProperties(
    val enabled: Boolean = false,
    val limit: Long = 60,
    val windowSeconds: Long = 60,
    val key: RateLimitKey = RateLimitKey.PRINCIPAL_OR_IP,
    val maxKeys: Long = 10_000,
) {
    enum class RateLimitKey {
        PRINCIPAL_OR_IP,
        IP,
        PRINCIPAL,
    }
}
