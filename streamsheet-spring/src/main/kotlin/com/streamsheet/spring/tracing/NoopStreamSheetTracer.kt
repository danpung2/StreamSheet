package com.streamsheet.spring.tracing

object NoopStreamSheetTracer : StreamSheetTracer {
    override fun <T> inSpan(name: String, block: () -> T): T = block()
}
