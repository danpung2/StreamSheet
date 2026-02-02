package com.streamsheet.demo.domain.model

import com.streamsheet.core.annotation.ExcelColumn
import com.streamsheet.core.annotation.ExcelSheet
import java.time.LocalDateTime

@ExcelSheet(name = "사용자 활동 로그")
data class UserActivity(
    @ExcelColumn(header = "ID", order = 1)
    val id: String,

    @ExcelColumn(header = "사용자명", order = 2)
    val username: String,

    @ExcelColumn(header = "활동 유형", order = 3)
    val activityType: String,

    @ExcelColumn(header = "설명", order = 4)
    val description: String,

    @ExcelColumn(header = "생성 일시", order = 5)
    val createdAt: LocalDateTime
)
