package com.streamsheet.spring.autoconfigure

import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
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
                .credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Props.accessKey, s3Props.secretKey)
                    )
                )

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
            // NOTE: 기본적으로 GOOGLE_APPLICATION_CREDENTIALS 환경 변수 또는 Default Credentials를 사용
            // Explicit key file loading could be added here if needed using properties.storage.gcs.credentialsPath
            return StorageOptions.getDefaultInstance().service
        }

        @Bean
        @ConditionalOnMissingBean
        fun fileStorage(storage: Storage, properties: StreamSheetProperties): FileStorage {
            return GcsFileStorage(storage, properties.storage.gcs.bucket)
        }
    }
}
