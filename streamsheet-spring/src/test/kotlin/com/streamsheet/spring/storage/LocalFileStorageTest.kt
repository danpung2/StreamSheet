package com.streamsheet.spring.storage

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@DisplayName("LocalFileStorage 테스트")
class LocalFileStorageTest {

    @Test
    @DisplayName("파일 저장 시 지정된 디렉토리에 파일이 생성되어야 한다")
    fun `save() should create file in base directory`(@TempDir tempDir: Path) {
        // Given
        val storage = LocalFileStorage(tempDir)
        val fileName = "test-file.txt"
        val content = "Hello World"
        val inputStream = ByteArrayInputStream(content.toByteArray())

        // When
        val uri = storage.save(fileName, inputStream, "text/plain")

        // Then
        val storedPath = tempDir.resolve(fileName)
        assertTrue(storedPath.exists())
        assertEquals(content, Files.readString(storedPath))
        assertEquals(storedPath.toUri(), uri)
    }

    @Test
    @DisplayName("파일 삭제 시 해당 파일이 제거되어야 한다")
    fun `delete() should remove the file`(@TempDir tempDir: Path) {
        // Given
        val storage = LocalFileStorage(tempDir)
        val fileName = "delete-me.txt"
        val filePath = tempDir.resolve(fileName)
        Files.writeString(filePath, "DELETE ME")
        
        assertTrue(filePath.exists())

        // When
        storage.delete(filePath.toUri())

        // Then
        assertFalse(filePath.exists())
    }

    @Test
    @DisplayName("존재하지 않는 파일 삭제 시 예외를 던지지 않고 로그만 남겨야 한다")
    fun `delete() should not throw exception for non-existent file`(@TempDir tempDir: Path) {
        // Given
        val storage = LocalFileStorage(tempDir)
        val nonExistentUri = tempDir.resolve("nothing.txt").toUri()

        // When & Then
        assertDoesNotThrow {
            storage.delete(nonExistentUri)
        }
    }
}
