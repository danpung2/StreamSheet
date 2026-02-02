package com.streamsheet.demo.domain.mongo

import com.streamsheet.core.annotation.ExcelColumn
import com.streamsheet.core.annotation.ExcelSheet
import java.time.LocalDateTime

/**
 * 엑셀 내보내기용 프로젝션 DTO
 * Projection DTO for Excel export
 * 
 * NOTE: 실제 엔티티(UserActivityDocument)의 필드 중 일부만 정의하여 
 * DB 조회 시점부터 메모리와 네트워크 성능을 최적화합니다.
 */
@ExcelSheet(name = "사용자 활동 로그 (Projection)")
data class UserActivityProjection(
    @ExcelColumn(header = "사용자명", order = 1)
    val username: String,

    @ExcelColumn(header = "활동", order = 2)
    val activityType: String,

    @ExcelColumn(header = "기록일시", order = 3, pattern = "yyyy-MM-dd HH:mm:ss")
    val createdAt: LocalDateTime
)
