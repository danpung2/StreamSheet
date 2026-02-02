package com.streamsheet.spring.autoconfigure

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import java.io.FileInputStream
import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.metrics.StreamSheetMetrics
import com.streamsheet.core.exporter.ExcelExporter
import com.streamsheet.core.exporter.SxssfExcelExporter
import com.streamsheet.spring.async.AsyncExportService
import com.streamsheet.spring.async.AsyncExportWorker
import com.streamsheet.spring.async.InMemoryJobManager
import com.streamsheet.spring.async.JobManager
import com.streamsheet.spring.async.RedisJobManager
import com.streamsheet.spring.metrics.MicrometerStreamSheetMetrics
import com.streamsheet.spring.storage.FileStorage
import com.streamsheet.spring.storage.RetryingFileStorage
import com.streamsheet.spring.storage.GcsFileStorage
import com.streamsheet.spring.storage.LocalFileStorage
import com.streamsheet.spring.storage.S3FileStorage
import com.streamsheet.spring.web.LocalFileController
import com.streamsheet.spring.tracing.NoopStreamSheetTracer
import com.streamsheet.spring.tracing.StreamSheetTracer
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
import org.springframework.data.redis.core.StringRedisTemplate
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.retry.backoff.ExponentialBackOffPolicy
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplate
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
    fun excelExportConfig(
        properties: StreamSheetProperties,
        metricsProvider: org.springframework.beans.factory.ObjectProvider<StreamSheetMetrics>
    ): ExcelExportConfig {
        val metrics = metricsProvider.ifAvailable ?: StreamSheetMetrics.NOOP
        return ExcelExportConfig(
            rowAccessWindowSize = properties.rowAccessWindowSize,
            flushBatchSize = properties.flushBatchSize,
            compressTempFiles = properties.compressTempFiles,
            applyHeaderStyle = properties.applyHeaderStyle,
            applyDataBorders = properties.applyDataBorders,
            preventFormulaInjection = properties.preventFormulaInjection,
            enableMetrics = properties.enableMetrics,
            maxRows = properties.maxRows,
            metrics = metrics,
        )
    }

    @Bean
    @ConditionalOnMissingBean
    fun excelExporter(config: ExcelExportConfig): ExcelExporter {
        // 현재는 SxssfExcelExporter를 기본 구현체로 사용
        return SxssfExcelExporter()
    }

    @Configuration
    @ConditionalOnProperty(prefix = "streamsheet", name = ["enable-metrics"], havingValue = "true")
    class StreamSheetMetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun streamSheetMetrics(registry: MeterRegistry): StreamSheetMetrics {
            return MicrometerStreamSheetMetrics(registry)
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "streamsheet", name = ["retry-enabled"], havingValue = "true")
    class StreamSheetRetryConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun streamSheetRetryTemplate(properties: StreamSheetProperties): RetryTemplate {
            val retryPolicy = SimpleRetryPolicy(properties.retryMaxAttempts)
            val backOffPolicy = ExponentialBackOffPolicy().apply {
                initialInterval = properties.retryInitialDelayMs
                multiplier = properties.retryMultiplier
                maxInterval = properties.retryMaxDelayMs
            }

            return RetryTemplate().apply {
                setRetryPolicy(retryPolicy)
                setBackOffPolicy(backOffPolicy)
            }
        }

        @Bean("streamSheetRetryingFileStorage")
        @Primary
        fun retryingFileStorage(
            @Qualifier("fileStorage") delegate: FileStorage,
            retryTemplate: RetryTemplate,
        ): FileStorage {
            return RetryingFileStorage(delegate, retryTemplate)
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "streamsheet", name = ["job-store"], havingValue = "IN_MEMORY", matchIfMissing = true)
    class InMemoryJobStoreConfiguration {

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
    }

    @Configuration
    @ConditionalOnProperty(prefix = "streamsheet", name = ["job-store"], havingValue = "REDIS")
    @ConditionalOnClass(StringRedisTemplate::class)
    class RedisJobStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean
        fun jobManager(
            redisTemplate: StringRedisTemplate,
            properties: StreamSheetProperties,
        ): JobManager {
            return RedisJobManager(
                redisTemplate = redisTemplate,
                keyPrefix = properties.jobKeyPrefix,
                retentionHours = properties.retentionHours,
            )
        }
    }

    @Bean
    @ConditionalOnMissingBean
    fun asyncExportService(
        jobManager: JobManager,
        worker: AsyncExportWorker,
    ): AsyncExportService {
        return AsyncExportService(jobManager, worker)
    }

    @Bean
    @ConditionalOnMissingBean
    fun streamSheetTracer(): StreamSheetTracer {
        return NoopStreamSheetTracer
    }

    @Bean
    @ConditionalOnMissingBean
    fun asyncExportWorker(
        jobManager: JobManager,
        fileStorage: FileStorage,
        excelExporter: ExcelExporter,
        config: ExcelExportConfig,
        eventPublisher: ApplicationEventPublisher,
        tracer: StreamSheetTracer,
    ): AsyncExportWorker {
        return AsyncExportWorker(jobManager, fileStorage, excelExporter, config, eventPublisher, tracer)
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

            // NOTE: 커스텀 엔드포인트 설정 (MinIO 등) 및 SSRF 방지 검증
            // Configure custom endpoint (for MinIO, etc.) and validate for SSRF prevention
            if (!s3Props.endpoint.isNullOrEmpty()) {
                val endpointUri = URI.create(s3Props.endpoint!!)

                // Whitelist validation
                if (s3Props.allowedEndpoints.isNotEmpty()) {
                    val host = endpointUri.host
                    requireNotNull(host) { "Endpoint URI must have a valid host" }

                    val isAllowed = s3Props.allowedEndpoints.any { allowed ->
                        host.equals(allowed, ignoreCase = true) || host.endsWith(".$allowed", ignoreCase = true)
                    }

                    if (!isAllowed) {
                        throw IllegalArgumentException(
                            "S3 endpoint host '$host' is not in the allowed list: ${s3Props.allowedEndpoints}"
                        )
                    }
                }

                builder.endpointOverride(endpointUri)
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
                    
                    // NOTE: 경로 순회 및 심볼릭 링크 공격 방지를 위해 canonicalFile 확인
                    // Verify canonical file to prevent path traversal and symbolic link attacks
                    val canonicalFile = credentialsFile.canonicalFile
                    require(canonicalFile.exists() && canonicalFile.isFile) {
                        "GCS credentials file not found or invalid: ${gcsProps.credentialsPath} (resolved: ${canonicalFile.path})"
                    }

                    val credentials = FileInputStream(canonicalFile).use { stream ->
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
