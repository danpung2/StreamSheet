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
    /**
     * S3 버킷 이름
     * S3 bucket name
     */
    var bucket: String = "",

    /**
     * AWS 리전 (기본값: us-east-1)
     * AWS region (default: us-east-1)
     */
    var region: String = "us-east-1",

    /**
     * AWS Default Credentials Provider 사용 여부 (기본값: true)
     * Whether to use AWS Default Credentials Provider (default: true)
     *
     * NOTE: true인 경우 다음 순서로 자격증명을 검색합니다:
     * When true, credentials are resolved in the following order:
     * 1. 환경 변수 (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
     * 2. 시스템 속성 (aws.accessKeyId, aws.secretAccessKey)
     * 3. AWS 프로필 파일 (~/.aws/credentials)
     * 4. ECS 컨테이너 자격증명
     * 5. EC2 인스턴스 프로필 (IAM Role)
     *
     * false인 경우 accessKey와 secretKey를 직접 사용합니다.
     * When false, accessKey and secretKey are used directly.
     */
    var useDefaultCredentialsProvider: Boolean = true,

    /**
     * AWS Access Key (useDefaultCredentialsProvider=false인 경우 사용)
     * AWS Access Key (used when useDefaultCredentialsProvider=false)
     *
     * WARNING: 프로덕션 환경에서는 환경 변수나 IAM Role 사용을 권장합니다.
     * For production, using environment variables or IAM roles is recommended.
     *
     * 환경 변수로 설정 시 (When setting via environment variable):
     * export STREAMSHEET_STORAGE_S3_ACCESS_KEY=your-access-key
     */
    var accessKey: String = "",

    /**
     * AWS Secret Key (useDefaultCredentialsProvider=false인 경우 사용)
     * AWS Secret Key (used when useDefaultCredentialsProvider=false)
     *
     * WARNING: 프로덕션 환경에서는 환경 변수나 IAM Role 사용을 권장합니다.
     * For production, using environment variables or IAM roles is recommended.
     *
     * 환경 변수로 설정 시 (When setting via environment variable):
     * export STREAMSHEET_STORAGE_S3_SECRET_KEY=your-secret-key
     */
    var secretKey: String = "",

    /**
     * 커스텀 S3 엔드포인트 (MinIO 또는 S3 호환 서비스용)
     * Custom S3 endpoint (for MinIO or S3-compatible services)
     *
     * NOTE: SSRF 방지를 위해 프로덕션에서는 화이트리스트 검증을 권장합니다.
     * For SSRF prevention, whitelist validation is recommended in production.
     */
    var endpoint: String? = null,

    /**
     * 허용된 S3 엔드포인트 호스트 목록 (SSRF 방지용)
     * List of allowed S3 endpoint hosts (for SSRF prevention)
     *
     * 예: ["minio.internal", "s3.custom-domain.com"]
     * Example: ["minio.internal", "s3.custom-domain.com"]
     *
     * NOTE: 이 목록이 비어있지 않으면, endpoint 설정 시 호스트가 이 목록에 포함되어야 합니다.
     * If this list is not empty, the endpoint host must be included in this list when configuring the endpoint.
     */
    var allowedEndpoints: List<String> = emptyList()
)

data class GcsStorageProperties(
    /**
     * GCS 버킷 이름
     * GCS bucket name
     */
    var bucket: String = "",

    /**
     * GCP 프로젝트 ID
     * GCP project ID
     */
    var projectId: String = "",

    /**
     * 서비스 계정 JSON 키 파일 경로 (선택)
     * Path to service account JSON key file (optional)
     *
     * NOTE: 지정하지 않으면 다음 순서로 자격증명을 검색합니다:
     * When not specified, credentials are resolved in the following order:
     * 1. GOOGLE_APPLICATION_CREDENTIALS 환경 변수
     * 2. Cloud SDK 기본 자격증명 (gcloud auth application-default login)
     * 3. GCE/GKE 메타데이터 서버 (서비스 계정)
     *
     * 환경 변수로 설정 시 (When setting via environment variable):
     * export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json
     *
     * 또는 프로퍼티로 설정 (Or set via property):
     * streamsheet.storage.gcs.credentials-path=/path/to/service-account.json
     */
    var credentialsPath: String? = null,

    /**
     * Application Default Credentials 사용 여부 (기본값: true)
     * Whether to use Application Default Credentials (default: true)
     *
     * NOTE: true인 경우 GOOGLE_APPLICATION_CREDENTIALS 환경 변수 또는
     * GCE/GKE 서비스 계정을 자동으로 사용합니다.
     * credentialsPath가 지정된 경우 해당 파일을 우선 사용합니다.
     * When true, automatically uses GOOGLE_APPLICATION_CREDENTIALS env var
     * or GCE/GKE service account. If credentialsPath is specified, it takes priority.
     */
    var useApplicationDefaultCredentials: Boolean = true
)
