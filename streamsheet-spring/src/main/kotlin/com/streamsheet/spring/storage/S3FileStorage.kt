package com.streamsheet.spring.storage

import org.slf4j.LoggerFactory
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream
import java.net.URI

class S3FileStorage(
    private val s3Client: S3Client,
    private val bucket: String
) : FileStorage {

    private val logger = LoggerFactory.getLogger(S3FileStorage::class.java)

    override fun save(fileName: String, inputStream: InputStream, contentType: String, contentLength: Long): URI {
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(fileName)
            .contentType(contentType)
            .build()
        
        // NOTE: contentLength 정보를 사용하여 메모리 버퍼링 없이 스트리밍 업로드 수행
        // Optimize: Use contentLength to enable streaming upload without buffering entire file in memory
        s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength))
        
        // Pre-signed URL generation logic can be added here
        val uri = URI.create("s3://$bucket/$fileName")
        logger.info("File uploaded to S3: {}", uri)
        return uri
    }

    override fun delete(fileUri: URI) {
        if (fileUri.scheme != "s3") {
            logger.warn("Invalid S3 URI scheme: {}", fileUri)
            return
        }

        // URI format: s3://bucket-name/key/path/to/file
        val bucketName = fileUri.host
        val key = fileUri.path.removePrefix("/")

        if (bucketName.isNullOrBlank() || key.isBlank()) {
            logger.warn("Invalid S3 URI format: {}", fileUri)
            return
        }

        runCatching {
            s3Client.deleteObject { builder ->
                builder.bucket(bucketName).key(key)
            }
            logger.info("Deleted file from S3: bucket={}, key={}", bucketName, key)
        }.onFailure { e ->
            logger.error("Failed to delete file from S3: uri={}, error={}", fileUri, e.message, e)
        }
    }
}
