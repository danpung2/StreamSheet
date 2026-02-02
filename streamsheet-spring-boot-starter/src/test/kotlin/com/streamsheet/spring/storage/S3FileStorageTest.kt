package com.streamsheet.spring.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.ByteArrayInputStream
import java.net.URI

@ExtendWith(MockitoExtension::class)
@DisplayName("S3FileStorage 테스트")
class S3FileStorageTest {

    @Mock
    private lateinit var s3Client: S3Client

    private val bucket = "test-bucket"

    @Test
    @DisplayName("S3에 파일 저장 시 putObject가 호출되어야 한다")
    fun `save() should call putObject`() {
        // Given
        val storage = S3FileStorage(s3Client, bucket)
        val fileName = "test.xlsx"
        val content = "test content"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val contentType = "application/vnd.ms-excel"

        // When
        val uri = storage.save(fileName, inputStream, contentType, content.length.toLong())

        // Then
        verify(s3Client).putObject(any<PutObjectRequest>(), any<RequestBody>())
        assertEquals(URI.create("s3://$bucket/$fileName"), uri)
    }
}
