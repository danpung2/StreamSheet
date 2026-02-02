package com.streamsheet.demo.api

import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exporter.SxssfExcelExporter
import com.streamsheet.core.schema.AnnotationExcelSchema
import com.streamsheet.demo.domain.jpa.UserActivityEntity
import com.streamsheet.demo.domain.jpa.UserActivityJpaRepository
import com.streamsheet.demo.domain.model.UserActivity
import com.streamsheet.demo.domain.mongo.UserActivityDocument
import com.streamsheet.demo.domain.mongo.UserActivityProjection
import com.streamsheet.demo.domain.mongo.UserActivityRepository
import com.streamsheet.jdbc.JdbcStreamingDataSource
import com.streamsheet.jpa.JpaStreamingDataSource
import com.streamsheet.mongodb.MongoStreamingDataSource
import com.streamsheet.spring.async.AsyncExportService
import com.streamsheet.spring.async.JobManager
import jakarta.persistence.EntityManager
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.io.File
import java.io.FileOutputStream
import java.sql.ResultSet
import java.time.LocalDateTime
import java.util.*

/**
 * Demo Controller for StreamSheet
 * 
 * Provides endpoints for seeding demo data and exporting to Excel using different data sources:
 * - Mock (in-memory)
 * - MongoDB
 * - JDBC (PostgreSQL)
 * - JPA (PostgreSQL)
 */
@RestController
@RequestMapping("/api/demo")
class DemoController(
    private val asyncExportService: AsyncExportService,
    private val jobManager: JobManager,
    private val mongoTemplate: MongoTemplate,
    private val userActivityRepository: UserActivityRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val entityManager: EntityManager,
    private val userActivityJpaRepository: UserActivityJpaRepository
) {

    // ==================== MongoDB ====================

    /**
     * MongoDB 데이터 시딩 (테스트용 데이터 생성)
     * Seed test data into MongoDB
     */
    @PostMapping("/mongodb/seed")
    fun seedMongo(@RequestParam(defaultValue = "1000") count: Int): Map<String, Any> {
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
     * Asynchronous Excel export using MongoDB Streaming
     */
    @PostMapping("/mongodb/export")
    fun exportMongo(): Map<String, String> {
        val schema = AnnotationExcelSchema(UserActivityDocument::class)
        
        // NOTE: 이 데이터 소스는 AutoCloseable를 상속하므로 AsyncExportService에서 작업 후 안전하게 close 됩니다.
        // This data source extends AutoCloseable, so AsyncExportService will safely close it after use.
        val dataSource = MongoStreamingDataSource.create<UserActivityDocument>(mongoTemplate)

        val jobId = asyncExportService.startExport(schema, dataSource)
        return mapOf(
            "jobId" to jobId, 
            "status" to "ACCEPTED",
            "statusUrl" to "/api/demo/status/$jobId"
        )
    }

    /**
     * 고도화된 MongoDB 내보내기 (Projection + Tuning Query)
     * Optimized MongoDB export with Projection
     */
    @PostMapping("/mongodb/export/optimized")
    fun exportOptimizedMongo(): Map<String, String> {
        val schema = AnnotationExcelSchema(UserActivityProjection::class)
        
        val query = Query().apply {
            addCriteria(Criteria.where("activityType").`is`("SEARCH"))
            with(Sort.by(Sort.Order.desc("createdAt")))
        }

        val dataSource = MongoStreamingDataSource.createWithProjection<UserActivityDocument, UserActivityProjection>(
            mongoTemplate, 
            query
        )

        val jobId = asyncExportService.startExport(schema, dataSource)
        return mapOf(
            "jobId" to jobId, 
            "status" to "ACCEPTED",
            "statusUrl" to "/api/demo/status/$jobId"
        )
    }

    /**
     * 직접 파일 다운로드 (동기 방식)
     * Direct file download (Synchronous)
     */
    @GetMapping("/mongodb/export/download")
    fun downloadMongoDirect(): ResponseEntity<Resource> {
        val schema = AnnotationExcelSchema(UserActivityDocument::class)
        val dataSource = MongoStreamingDataSource.create<UserActivityDocument>(mongoTemplate)
        
        val tempFile = File.createTempFile("mongo-export-", ".xlsx")
        val exporter = SxssfExcelExporter()
        FileOutputStream(tempFile).use { output ->
            exporter.export(schema, dataSource, output)
        }
        
        val resource = FileSystemResource(tempFile)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mongodb-direct-export.xlsx\"")
            .body(resource)
    }

    // ==================== PostgreSQL (JPA) ====================

    /**
     * PostgreSQL 데이터 시딩 (JPA 사용)
     * Seed test data into PostgreSQL via JPA
     */
    @PostMapping("/postgres/seed")
    fun seedPostgres(@RequestParam(defaultValue = "1000") count: Int): Map<String, Any> {
        val activities = (1..count).map {
            UserActivityEntity(
                id = UUID.randomUUID().toString(),
                username = "PgUser-$it",
                activityType = if (it % 2 == 0) "PURCHASE" else "CLICK",
                description = "PostgreSQL activity log for user $it",
                createdAt = LocalDateTime.now().minusHours(it.toLong())
            )
        }
        userActivityJpaRepository.saveAll(activities)
        return mapOf("message" to "Successfully seeded $count records to PostgreSQL", "totalCount" to userActivityJpaRepository.count())
    }

    /**
     * JPA 기반 동기 엑셀 내보내기 (직접 다운로드)
     * Synchronous Excel export using JPA Stream (Direct Download)
     * 
     * NOTE: JPA Stream은 활성 트랜잭션 내에서만 동작합니다.
     * 비동기 처리가 필요하면 JDBC 방식을 사용하세요.
     * JPA Stream only works within an active transaction.
     * For async processing, use JDBC approach instead.
     */
    @GetMapping("/jpa/export/download")
    @Transactional(readOnly = true)
    fun downloadJpaDirect(): ResponseEntity<Resource> {
        val schema = AnnotationExcelSchema(UserActivityEntity::class)
        
        val dataSource = JpaStreamingDataSource(
            entityManager = entityManager,
            streamProvider = {
                entityManager.createQuery("SELECT u FROM UserActivityEntity u ORDER BY u.createdAt DESC", UserActivityEntity::class.java)
                    .resultStream
            }
        )
        
        val tempFile = File.createTempFile("jpa-export-", ".xlsx")
        val exporter = SxssfExcelExporter()
        FileOutputStream(tempFile).use { output ->
            exporter.export(schema, dataSource, output)
        }
        
        val resource = FileSystemResource(tempFile)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"jpa-direct-export.xlsx\"")
            .body(resource)
    }

    // ==================== PostgreSQL (JDBC) ====================

    /**
     * JDBC 기반 비동기 엑셀 내보내기 (ResultSet Streaming)
     * Asynchronous Excel export using JDBC ResultSet streaming
     * 
     * NOTE: JDBC ResultSet은 DB 커서를 유지하고 스트리밍하므로 메모리 효율이 가장 좋습니다.
     * ResultSet keeps DB cursor open and streams, offering optimal memory efficiency.
     */
    @PostMapping("/jdbc/export")
    fun exportJdbc(): Map<String, String> {
        val schema = AnnotationExcelSchema(UserActivityEntity::class)
        
        val rowMapper = RowMapper { rs: ResultSet, _: Int ->
            UserActivityEntity(
                id = rs.getString("id"),
                username = rs.getString("username"),
                activityType = rs.getString("activity_type"),
                description = rs.getString("description"),
                createdAt = rs.getTimestamp("created_at").toLocalDateTime()
            )
        }

        val dataSource = JdbcStreamingDataSource(
            jdbcTemplate = jdbcTemplate,
            sql = "SELECT id, username, activity_type, description, created_at FROM user_activities ORDER BY created_at DESC",
            rowMapper = rowMapper,
            fetchSize = 1000
        )

        val jobId = asyncExportService.startExport(schema, dataSource)
        return mapOf(
            "jobId" to jobId, 
            "status" to "ACCEPTED",
            "statusUrl" to "/api/demo/status/$jobId"
        )
    }

    // ==================== Mock (In-Memory) ====================

    /**
     * Mock 데이터 기반 비동기 엑셀 내보내기
     * Asynchronous Excel export using in-memory mock data
     */
    @PostMapping("/export")
    fun export(@RequestParam(defaultValue = "10000") count: Int): Map<String, String> {
        val schema = AnnotationExcelSchema(UserActivity::class)
        
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
        return mapOf(
            "jobId" to jobId, 
            "status" to "ACCEPTED",
            "statusUrl" to "/api/demo/status/$jobId"
        )
    }

    // ==================== Job Status ====================

    @GetMapping("/status/{jobId}")
    fun getStatus(@PathVariable jobId: String) = jobManager.getJob(jobId)
}

