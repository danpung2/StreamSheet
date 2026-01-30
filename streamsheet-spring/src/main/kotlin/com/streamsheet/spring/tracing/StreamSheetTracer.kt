package com.streamsheet.spring.tracing

interface StreamSheetTracer {
    fun <T> inSpan(name: String, block: () -> T): T
}
