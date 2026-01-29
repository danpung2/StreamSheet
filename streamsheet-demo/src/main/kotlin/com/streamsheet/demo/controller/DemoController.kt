package com.streamsheet.demo.controller

import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.schema.AnnotationExcelSchema
import com.streamsheet.demo.domain.UserActivity
import com.streamsheet.spring.async.AsyncExportService
import com.streamsheet.spring.async.JobManager
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/api/demo")
class DemoController(
    private val asyncExportService: AsyncExportService,
    private val jobManager: JobManager
) {

    @PostMapping("/export")
    fun export(@RequestParam(defaultValue = "10000") count: Int): Map<String, String> {
        val schema = AnnotationExcelSchema(UserActivity::class)
        
        // Mock StreamingDataSource
        val dataSource = object : StreamingDataSource<UserActivity> {
            override fun stream(): Sequence<UserActivity> = sequence {
                for (i in 1..count) {
                    yield(
                        UserActivity(
                            id = UUID.randomUUID().toString(),
                            username = "User-$i",
                            activityType = if (i % 2 == 0) "LOGIN" else "LOGOUT",
                            description = "User activity description for index $i",
                            createdAt = LocalDateTime.now().minusMinutes(i.toLong())
                        )
                    )
                }
            }

            override fun close() {
                // No resources to close for mock data
            }
        }

        val jobId = asyncExportService.startExport(schema, dataSource)
        return mapOf("jobId" to jobId, "status" to "ACCEPTED")
    }

    @GetMapping("/status/{jobId}")
    fun getStatus(@PathVariable jobId: String) = jobManager.getJob(jobId)
}
