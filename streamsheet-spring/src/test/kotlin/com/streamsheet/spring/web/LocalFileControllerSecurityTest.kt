package com.streamsheet.spring.web

import com.streamsheet.spring.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest(classes = [TestApplication::class], webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "streamsheet.storage.type=LOCAL",
        "streamsheet.security.enabled=true",
        "streamsheet.security.permit-authenticated=true",
        "streamsheet.security.permit-all=false",
    ]
)
@DisplayName("LocalFileController 보안 테스트")
class LocalFileControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    @DisplayName("인증되지 않은 요청은 다운로드할 수 없어야 한다")
    fun `unauthenticated request should not download`() {
        val fileName = "security-test.xlsx"
        val file = tempDir.resolve(fileName)
        Files.write(file, "dummy".toByteArray())

        val result = mockMvc.perform(get("/api/streamsheet/download/$fileName"))
            .andReturn()

        // Spring Security 구성에 따라 401/403/302(리다이렉트) 중 하나가 될 수 있음
        assertThat(result.response.status).isIn(401, 403, 302)
    }

    @Test
    @WithMockUser(username = "user")
    @DisplayName("인증된 요청은 파일 다운로드가 가능해야 한다")
    fun `authenticated request should download`() {
        val fileName = "security-test-auth.xlsx"
        val file = tempDir.resolve(fileName)
        Files.write(file, "dummy content".toByteArray())

        mockMvc.perform(get("/api/streamsheet/download/$fileName"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"$fileName\""))
            .andExpect(content().bytes("dummy content".toByteArray()))
    }

    companion object {
        private lateinit var tempDir: Path

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            tempDir = Files.createTempDirectory("streamsheet-security-test")
            registry.add("streamsheet.storage.local.path") { tempDir.toString() }
        }
    }
}
