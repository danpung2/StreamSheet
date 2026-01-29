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
        // Implement deletion logic parsing bucket/key from URI
        logger.info("Delete requested for S3 URI: {}", fileUri)
    }
}
