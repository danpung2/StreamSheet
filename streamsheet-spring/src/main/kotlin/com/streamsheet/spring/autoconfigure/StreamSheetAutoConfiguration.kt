package com.streamsheet.spring.autoconfigure

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import java.io.FileInputStream
import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.exporter.ExcelExporter
import com.streamsheet.core.exporter.SxssfExcelExporter
import com.streamsheet.spring.async.AsyncExportService
import com.streamsheet.spring.async.InMemoryJobManager
import com.streamsheet.spring.async.JobManager
import com.streamsheet.spring.storage.FileStorage
import com.streamsheet.spring.storage.GcsFileStorage
import com.streamsheet.spring.storage.LocalFileStorage
import com.streamsheet.spring.storage.S3FileStorage
import com.streamsheet.spring.web.LocalFileController
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.nio.file.Path

/**
 * StreamSheet 자동 설정
 * StreamSheet auto-configuration
 *
 * NOTE: StreamSheetProperties가 존재하면 자동으로 설정을 로드합니다.
 * Automatically loads configuration if StreamSheetProperties exists.
 */
@AutoConfiguration
@EnableAsync
@EnableConfigurationProperties(StreamSheetProperties::class)
@ConditionalOnClass(SxssfExcelExporter::class)
class StreamSheetAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun excelExportConfig(properties: StreamSheetProperties): ExcelExportConfig {
        return properties.toExcelExportConfig()
    }

    @Bean
    @ConditionalOnMissingBean
    fun excelExporter(config: ExcelExportConfig): ExcelExporter {
        // 현재는 SxssfExcelExporter를 기본 구현체로 사용
        return SxssfExcelExporter()
    }

    @Bean
    @ConditionalOnMissingBean
    fun jobManager(
        properties: StreamSheetProperties,
        fileStorageProvider: org.springframework.beans.factory.ObjectProvider<FileStorage>
    ): JobManager {
        return InMemoryJobManager(retentionHours = properties.retentionHours) { job ->
            job.resultUri?.let { uri ->
                fileStorageProvider.ifAvailable { storage ->
                    storage.delete(uri)
                }
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean
    fun asyncExportService(
        jobManager: JobManager,
        fileStorage: FileStorage,
        excelExporter: ExcelExporter,
        config: ExcelExportConfig,
        eventPublisher: ApplicationEventPublisher
    ): AsyncExportService {
        return AsyncExportService(jobManager, fileStorage, excelExporter, config, eventPublisher)
    }

    /**
     * Local Storage Configuration
     */
    @Configuration
    @ConditionalOnProperty(prefix = "streamsheet.storage", name = ["type"], havingValue = "LOCAL", matchIfMissing = true)
    class LocalStorageConfiguration {
        @Bean
        @ConditionalOnMissingBean
        fun fileStorage(properties: StreamSheetProperties): FileStorage {
            val path = Path.of(properties.storage.local.path)
            val baseUrl = properties.storage.local.baseUrl
            return LocalFileStorage(path, baseUrl)
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnWebApplication
        fun localFileController(properties: StreamSheetProperties): LocalFileController {
            return LocalFileController(properties)
        }
    }

    /**
     * S3 Storage Configuration
     */
    @Configuration
    @ConditionalOnProperty(prefix = "streamsheet.storage", name = ["type"], havingValue = "S3")
    @ConditionalOnClass(S3Client::class)
    class S3StorageConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun s3Client(properties: StreamSheetProperties): S3Client {
            val s3Props = properties.storage.s3
            val builder = S3Client.builder()
                .region(Region.of(s3Props.region))

            // NOTE: 자격증명 프로바이더 설정
            // Configure credentials provider
            if (s3Props.useDefaultCredentialsProvider) {
                // AWS Default Credentials Provider 사용 (환경변수, IAM Role 등)
                // Use AWS Default Credentials Provider (env vars, IAM role, etc.)
                builder.credentialsProvider(DefaultCredentialsProvider.create())
            } else {
                // 명시적 자격증명 사용 (개발/테스트 환경용)
                // Use explicit credentials (for dev/test environments)
                require(s3Props.accessKey.isNotBlank()) {
                    "S3 accessKey must be provided when useDefaultCredentialsProvider=false"
                }
                require(s3Props.secretKey.isNotBlank()) {
                    "S3 secretKey must be provided when useDefaultCredentialsProvider=false"
                }
                builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Props.accessKey, s3Props.secretKey)
                    )
                )
            }

            // NOTE: 커스텀 엔드포인트 설정 (MinIO 등)
            // Configure custom endpoint (for MinIO, etc.)
            if (!s3Props.endpoint.isNullOrEmpty()) {
                builder.endpointOverride(URI.create(s3Props.endpoint!!))
            }

            return builder.build()
        }

        @Bean
        @ConditionalOnMissingBean
        fun fileStorage(s3Client: S3Client, properties: StreamSheetProperties): FileStorage {
            return S3FileStorage(s3Client, properties.storage.s3.bucket)
        }
    }

    /**
     * GCS Storage Configuration
     */
    @Configuration
    @ConditionalOnProperty(prefix = "streamsheet.storage", name = ["type"], havingValue = "GCS")
    @ConditionalOnClass(Storage::class)
    class GcsStorageConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun googleCloudStorage(properties: StreamSheetProperties): Storage {
            val gcsProps = properties.storage.gcs
            val builder = StorageOptions.newBuilder()

            // NOTE: 프로젝트 ID 설정
            // Configure project ID
            if (gcsProps.projectId.isNotBlank()) {
                builder.setProjectId(gcsProps.projectId)
            }

            // NOTE: 자격증명 설정
            // Configure credentials
            when {
                // 1. credentialsPath가 지정된 경우 해당 파일 사용
                // Use specified credentials file if provided
                !gcsProps.credentialsPath.isNullOrBlank() -> {
                    val credentialsFile = java.io.File(gcsProps.credentialsPath!!)
                    require(credentialsFile.exists()) {
                        "GCS credentials file not found: ${gcsProps.credentialsPath}"
                    }
                    val credentials = FileInputStream(credentialsFile).use { stream ->
                        GoogleCredentials.fromStream(stream)
                    }
                    builder.setCredentials(credentials)
                }

                // 2. Application Default Credentials 사용 (환경변수, GCE/GKE 메타데이터 등)
                // Use Application Default Credentials (env var, GCE/GKE metadata, etc.)
                gcsProps.useApplicationDefaultCredentials -> {
                    // NOTE: Application Default Credentials를 시도하고, 없으면 NoCredentials 사용
                    // Try Application Default Credentials, fall back to NoCredentials if not found
                    val credentials = runCatching {
                        GoogleCredentials.getApplicationDefault()
                    }.getOrElse {
                        // 개발/테스트 환경에서 자격증명이 없는 경우
                        // When credentials are not available in dev/test environment
                        com.google.auth.oauth2.GoogleCredentials.create(null)
                    }
                    builder.setCredentials(credentials)
                }

                // 3. 명시적 자격증명 없이 기본 서비스 사용 (테스트용)
                // Use default service without explicit credentials (for testing)
                else -> {
                    // StorageOptions.getDefaultInstance()와 동일
                    // Same as StorageOptions.getDefaultInstance()
                }
            }

            return builder.build().service
        }

        @Bean
        @ConditionalOnMissingBean
        fun fileStorage(storage: Storage, properties: StreamSheetProperties): FileStorage {
            return GcsFileStorage(storage, properties.storage.gcs.bucket)
        }
    }
}
