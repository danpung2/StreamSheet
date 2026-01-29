package com.streamsheet.spring.storage

import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString

/**
 * 로컬 파일 시스템 저장소 구현체
 * Local file system storage implementation
 *
 * NOTE: 개발 환경이나 단일 서버 환경에서 임시로 파일을 저장할 때 사용합니다.
 * Used for storing temporary files in development or single-server environments.
 */
class LocalFileStorage(
    private val baseDir: Path
) : FileStorage {

    private val logger = LoggerFactory.getLogger(LocalFileStorage::class.java)

    init {
        Files.createDirectories(baseDir)
    }

    override fun save(fileName: String, inputStream: InputStream, contentType: String): URI {
        val targetPath = baseDir.resolve(fileName)
        Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING)
        logger.info("File saved locally: {}", targetPath.absolutePathString())
        return targetPath.toUri()
    }

    override fun delete(fileUri: URI) {
        try {
            val path = Path.of(fileUri)
            Files.deleteIfExists(path)
            logger.info("File deleted locally: {}", path.absolutePathString())
        } catch (e: Exception) {
            logger.warn("Failed to delete local file: {}", fileUri, e)
        }
    }
}
