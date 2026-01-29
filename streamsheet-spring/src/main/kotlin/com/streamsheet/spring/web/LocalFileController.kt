package com.streamsheet.spring.web

import com.streamsheet.spring.autoconfigure.StreamSheetProperties
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.exists

@RestController
class LocalFileController(
    private val properties: StreamSheetProperties
) {

    @GetMapping("\${streamsheet.storage.local.endpoint:/api/streamsheet/download}/{fileName}")
    fun download(@PathVariable fileName: String): ResponseEntity<Resource> {
        val baseDir = Path.of(properties.storage.local.path)
        val file = baseDir.resolve(fileName)

        if (!file.exists()) {
            return ResponseEntity.notFound().build()
        }

        val resource = FileSystemResource(file)
        
        // 인코딩된 파일명 처리
        val encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()).replace("+", "%20")
        
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$encodedFileName\"")
            .body(resource)
    }
}
