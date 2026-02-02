package com.streamsheet.spring.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.retry.backoff.NoBackOffPolicy
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplate
import java.io.ByteArrayInputStream
import java.net.URI

@DisplayName("RetryingFileStorage 테스트")
class RetryingFileStorageTest {

    @Test
    @DisplayName("save는 실패 시 재시도 후 성공해야 한다")
    fun `save should retry and succeed`() {
        val retryTemplate = RetryTemplate().apply {
            setRetryPolicy(SimpleRetryPolicy(3))
            setBackOffPolicy(NoBackOffPolicy())
        }

        val delegate = object : FileStorage {
            var attempts = 0

            override fun save(fileName: String, inputStream: java.io.InputStream, contentType: String, contentLength: Long): URI {
                attempts++
                if (attempts < 3) throw RuntimeException("transient")
                return URI.create("local://ok")
            }

            override fun delete(fileUri: URI) {}
        }

        val storage = RetryingFileStorage(delegate, retryTemplate)

        val uri = storage.save(
            fileName = "a.xlsx",
            inputStream = ByteArrayInputStream(byteArrayOf(1)),
            contentType = "application/octet-stream",
            contentLength = 1,
        )

        assertEquals("local://ok", uri.toString())
        assertEquals(3, delegate.attempts)
    }
}
