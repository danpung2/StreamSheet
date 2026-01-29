package com.streamsheet.spring.async

import java.net.URI

/**
 * 엑셀 내보내기 완료 이벤트
 * Excel export completion event
 */
data class ExportCompletedEvent(
    val jobId: String,
    val resultUri: URI,
    val success: Boolean,
    val errorMessage: String? = null
)
