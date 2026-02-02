package com.streamsheet.spring.storage

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI

@ExtendWith(MockitoExtension::class)
@DisplayName("GcsFileStorage 테스트")
class GcsFileStorageTest {

    @Mock
    private lateinit var storage: Storage

    private val bucket = "test-gcs-bucket"

    @Test
    @DisplayName("GCS에 파일 저장 시 createFrom이 호출되어야 한다")
    fun `save() should call createFrom`() {
        // Given
        val gcsStorage = GcsFileStorage(storage, bucket)
        val fileName = "test.xlsx"
        val content = "test content"
        val inputStream = ByteArrayInputStream(content.toByteArray())
        val contentType = "application/vnd.ms-excel"

        // When
        val uri = gcsStorage.save(fileName, inputStream, contentType, content.length.toLong())

        // Then
        verify(storage).createFrom(any<BlobInfo>(), any<InputStream>())
        assertEquals(URI.create("gs://$bucket/$fileName"), uri)
    }
}
