package com.streamsheet.spring.storage

import org.springframework.retry.support.RetryTemplate
import java.io.InputStream
import java.net.URI

class RetryingFileStorage(
    private val delegate: FileStorage,
    private val retryTemplate: RetryTemplate,
) : FileStorage {

    override fun save(fileName: String, inputStream: InputStream, contentType: String, contentLength: Long): URI {
        return retryTemplate.execute<URI, RuntimeException> {
            delegate.save(fileName, inputStream, contentType, contentLength)
        }
    }

    override fun delete(fileUri: URI) {
        retryTemplate.execute<Unit, RuntimeException> {
            delegate.delete(fileUri)
            Unit
        }
    }
}
