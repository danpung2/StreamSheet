package com.streamsheet.demo.controller

import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exporter.ExcelExporter

import com.streamsheet.core.exporter.SxssfExcelExporter
import com.streamsheet.core.schema.AnnotationExcelSchema
import com.streamsheet.demo.domain.UserActivity
import com.streamsheet.demo.domain.UserActivityDocument
import com.streamsheet.demo.domain.UserActivityProjection
import com.streamsheet.demo.domain.UserActivityRepository
import com.streamsheet.mongodb.MongoStreamingDataSource
import com.streamsheet.spring.async.AsyncExportService
import com.streamsheet.spring.async.JobManager
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/api/demo")
class DemoController(
    private val asyncExportService: AsyncExportService,
    private val jobManager: JobManager,
    private val mongoTemplate: MongoTemplate,
    private val userActivityRepository: UserActivityRepository
) {

    /**
     * MongoDB 데이터 시딩 (테스트용 데이터 생성)
     */
    @PostMapping("/mongodb/seed")
    fun seed(@RequestParam(defaultValue = "1000") count: Int): Map<String, Any> {
        val activities = (1..count).map {
            UserActivityDocument(
                username = "MongoUser-$it",
                activityType = if (it % 2 == 0) "SEARCH" else "VIEW",
                description = "MongoDB activity log for user $it",
                createdAt = LocalDateTime.now().minusHours(it.toLong())
            )
        }
        userActivityRepository.saveAll(activities)
        return mapOf("message" to "Successfully seeded $count records to MongoDB", "totalCount" to userActivityRepository.count())
    }

    /**
     * MongoDB 기반 비동기 엑셀 내보내기
     */
    @PostMapping("/mongodb/export")
    fun exportMongo(): Map<String, String> {
        val schema = AnnotationExcelSchema(UserActivityDocument::class)
        
        // streamsheet-mongodb 모듈의 MongoStreamingDataSource 사용
        // NOTE: 이 데이터 소스는 AutoCloseable를 상속하므로 AsyncExportService에서 작업 후 안전하게 close 됩니다.
        val dataSource = MongoStreamingDataSource.create<UserActivityDocument>(mongoTemplate)

        val jobId = asyncExportService.startExport(schema, dataSource)
        return mapOf("jobId" to jobId, "status" to "ACCEPTED")
    }

    /**
     * 고도화된 MongoDB 내보내기 (Projection + Tuning Query)
     */
    @PostMapping("/mongodb/export/optimized")
    fun exportOptimizedMongo(): Map<String, String> {
        // 1. 엑셀 스키마는 DTO(UserActivityProjection) 기반으로 생성
        val schema = AnnotationExcelSchema(UserActivityProjection::class)
        
        // 2. 튜닝된 쿼리 작성 (Index Hint, 정렬, 필터 포함)
        val query = Query().apply {
            addCriteria(Criteria.where("activityType").`is`("SEARCH")) // 특정 타입만 필터
            with(Sort.by(Sort.Order.desc("createdAt"))) // 최신순 정렬
        }

        // 3. MongoStreamingDataSource를 Projection 모드로 생성
        // UserActivityDocument에서 읽어서 UserActivityProjection으로 자동 매핑
        val dataSource = MongoStreamingDataSource.createWithProjection<UserActivityDocument, UserActivityProjection>(
            mongoTemplate, 
            query
        )

        val jobId = asyncExportService.startExport(schema, dataSource)
        return mapOf(
            "jobId" to jobId, 
            "status" to "ACCEPTED",
            "statusUrl" to "http://localhost:8080/api/demo/status/$jobId"
        )
    }

    /**
     * 직접 파일 다운로드 (동기 방식)
     * Direct file download (Synchronous)
     * NOTE: 대용량 데이터의 경우 브라우저 타임아웃이 발생할 수 있으므로 비동기 방식을 권장합니다.
     */
    @GetMapping("/mongodb/export/download")
    fun downloadMongoDirect(): ResponseEntity<Resource> {
        val schema = AnnotationExcelSchema(UserActivityDocument::class)
        val dataSource = MongoStreamingDataSource.create<UserActivityDocument>(mongoTemplate)
        
        // 임시 파일 생성
        val tempFile = File.createTempFile("mongo-export-", ".xlsx")
        
        // Exporter 객체를 직접 생성하여 사용
        val exporter = SxssfExcelExporter()
        FileOutputStream(tempFile).use { output ->
            exporter.export(schema, dataSource, output)
        }
        
        val resource = FileSystemResource(tempFile)
        val contentDisposition = "attachment; filename=\"mongodb-direct-export.xlsx\""
        
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
            .body(resource)
    }

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
