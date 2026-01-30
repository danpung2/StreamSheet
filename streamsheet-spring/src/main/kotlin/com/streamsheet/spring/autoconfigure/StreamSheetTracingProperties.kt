package com.streamsheet.spring.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "streamsheet.tracing")
data class StreamSheetTracingProperties(
    val enabled: Boolean = false,
)
