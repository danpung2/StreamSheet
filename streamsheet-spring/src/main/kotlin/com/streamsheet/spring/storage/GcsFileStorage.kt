package com.streamsheet.spring.storage

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI

class GcsFileStorage(
    private val storage: Storage,
    private val bucket: String
) : FileStorage {

    private val logger = LoggerFactory.getLogger(GcsFileStorage::class.java)

    override fun save(fileName: String, inputStream: InputStream, contentType: String, contentLength: Long): URI {
        val blobId = BlobId.of(bucket, fileName)
        val blobInfo = BlobInfo.newBuilder(blobId)
            .setContentType(contentType)
            .build()
            
        // NOTE: 최적화된 스트리밍 업로드 수행 (메모리 버퍼링 최소화)
        // NOTE: Perform optimized streaming upload (minimize memory buffering)
        storage.createFrom(blobInfo, inputStream)
        
        // NOTE: gs:// 프로토콜 기반의 URI 반환
        // NOTE: Returns URI based on gs:// protocol
        val uri = URI.create("gs://$bucket/$fileName")
        logger.info("File uploaded to GCS: {}", uri)
        return uri
    }

    override fun delete(fileUri: URI) {
        // Implement logic to parse bucket/blob from gs:// URI
        logger.info("Delete requested for GCS URI: {}", fileUri)
    }
}
