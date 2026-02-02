package com.streamsheet.demo.domain.jpa

import com.streamsheet.core.annotation.ExcelColumn
import com.streamsheet.core.annotation.ExcelSheet
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "user_activities")
@ExcelSheet(name = "사용자 활동 로그 (PostgreSQL)")
class UserActivityEntity(
    @Id
    @Column(length = 36)
    @ExcelColumn(header = "ID", order = 1)
    var id: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    @ExcelColumn(header = "Username", order = 2)
    var username: String = "",

    @Column(name = "activity_type", nullable = false)
    @ExcelColumn(header = "Activity Type", order = 3)
    var activityType: String = "",

    @Column(length = 1000)
    @ExcelColumn(header = "Description", order = 4)
    var description: String = "",

    @Column(name = "created_at", nullable = false)
    @ExcelColumn(header = "Created At", order = 5)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
