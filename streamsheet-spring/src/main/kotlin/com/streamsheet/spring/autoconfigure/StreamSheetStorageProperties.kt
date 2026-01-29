package com.streamsheet.spring.autoconfigure

import com.streamsheet.spring.storage.StorageType
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * 스토리지 설정 프로퍼티
 * Storage configuration properties
 */
data class StreamSheetStorageProperties(
    /**
     * 사용할 스토리지 유형 (기본값: LOCAL)
     * Storage type to use (default: LOCAL)
     */
    val type: StorageType = StorageType.LOCAL,

    /**
     * 로컬 스토리지 설정
     * Local storage configuration
     */
    @NestedConfigurationProperty
    val local: LocalStorageProperties = LocalStorageProperties(),

    /**
     * AWS S3 스토리지 설정
     * AWS S3 storage configuration
     */
    @NestedConfigurationProperty
    val s3: S3StorageProperties = S3StorageProperties(),

    /**
     * Google Cloud Storage 설정
     * Google Cloud Storage configuration
     */
    @NestedConfigurationProperty
    val gcs: GcsStorageProperties = GcsStorageProperties()
)

data class LocalStorageProperties(
    /**
     * 파일 저장 경로 (기본값: java.io.tmpdir)
     * File save path (default: java.io.tmpdir)
     */
    val path: String = System.getProperty("java.io.tmpdir"),
    
    /**
     * 다운로드 엔드포인트 경로 (기본값: /api/streamsheet/download)
     * Download endpoint path
     */
    val endpoint: String = "/api/streamsheet/download",

    /**
     * 다운로드 기본 URL (예: http://localhost:8080/api/streamsheet/download)
     * Base URL for download. If null, it is constructed relative to server root.
     * NOTE: For now, we use a simple string.
     */
    val baseUrl: String = "http://localhost:8080/api/streamsheet/download"
)

data class S3StorageProperties(
    var bucket: String = "",
    var region: String = "us-east-1",
    var accessKey: String = "",
    var secretKey: String = "",
    var endpoint: String? = null // For MinIO or unrelated S3 compatible services
)

data class GcsStorageProperties(
    var bucket: String = "",
    var projectId: String = "",
    var credentialsPath: String? = null // Path to JSON key file
)
