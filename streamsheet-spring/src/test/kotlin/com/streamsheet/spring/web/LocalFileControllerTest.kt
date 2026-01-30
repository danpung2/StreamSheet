package com.streamsheet.spring.web

import com.streamsheet.spring.autoconfigure.StreamSheetProperties
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Files

@WebMvcTest(LocalFileController::class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("LocalFileController 테스트")
class LocalFileControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var properties: StreamSheetProperties

    @Test
    @DisplayName("파일이 존재할 때 다운로드가 성공해야 한다")
    fun `download() should return file content when it exists`() {
        // Given
        val tempDir = Files.createTempDirectory("streamsheet-test")
        val fileName = "test-download.xlsx"
        val file = tempDir.resolve(fileName)
        Files.write(file, "dummy content".toByteArray())

        // Mock properties
        val storageProps = org.mockito.Mockito.mock(com.streamsheet.spring.autoconfigure.StreamSheetStorageProperties::class.java)
        val localProps = org.mockito.Mockito.mock(com.streamsheet.spring.autoconfigure.LocalStorageProperties::class.java)
        
        org.mockito.Mockito.`when`(properties.storage).thenReturn(storageProps)
        org.mockito.Mockito.`when`(storageProps.local).thenReturn(localProps)
        org.mockito.Mockito.`when`(localProps.path).thenReturn(tempDir.toString())
        org.mockito.Mockito.`when`(localProps.endpoint).thenReturn("/api/streamsheet/download")

        // When & Then
        mockMvc.perform(get("/api/streamsheet/download/$fileName"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"$fileName\""))
            .andExpect(content().bytes("dummy content".toByteArray()))

        // Cleanup
        file.toFile().delete()
        tempDir.toFile().delete()
    }

    @Test
    @DisplayName("파일이 존재하지 않으면 404를 반환해야 한다")
    fun `download() should return 404 when file does not exist`() {
        // Given
        val tempDir = Files.createTempDirectory("streamsheet-test-empty")
        
        val storageProps = org.mockito.Mockito.mock(com.streamsheet.spring.autoconfigure.StreamSheetStorageProperties::class.java)
        val localProps = org.mockito.Mockito.mock(com.streamsheet.spring.autoconfigure.LocalStorageProperties::class.java)
        
        org.mockito.Mockito.`when`(properties.storage).thenReturn(storageProps)
        org.mockito.Mockito.`when`(storageProps.local).thenReturn(localProps)
        org.mockito.Mockito.`when`(localProps.path).thenReturn(tempDir.toString())

        // When & Then
        mockMvc.perform(get("/api/streamsheet/download/non-existent.xlsx"))
            .andExpect(status().isNotFound)

        // Cleanup
        tempDir.toFile().delete()
    }
}
