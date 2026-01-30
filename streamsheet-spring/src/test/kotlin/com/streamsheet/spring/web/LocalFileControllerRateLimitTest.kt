package com.streamsheet.spring.web

import com.streamsheet.spring.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest(classes = [TestApplication::class], webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "streamsheet.storage.type=LOCAL",
        "streamsheet.security.enabled=true",
        "streamsheet.security.permit-all=true",
        "streamsheet.security.permit-authenticated=false",
        "streamsheet.rate-limit.enabled=true",
        "streamsheet.rate-limit.limit=2",
        "streamsheet.rate-limit.window-seconds=60",
        "streamsheet.rate-limit.key=IP",
    ]
)
@DisplayName("LocalFileController rate limit 테스트")
class LocalFileControllerRateLimitTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    @DisplayName("동일 IP에서 제한 초과 시 429를 반환해야 한다")
    fun `should return 429 when rate limit exceeded`() {
        val fileName = "rate-limit-test.xlsx"
        val file = tempDir.resolve(fileName)
        Files.write(file, "dummy".toByteArray())

        val ok1 = mockMvc.perform(get("/api/streamsheet/download/$fileName")).andReturn()
        val ok2 = mockMvc.perform(get("/api/streamsheet/download/$fileName")).andReturn()
        val limited = mockMvc.perform(get("/api/streamsheet/download/$fileName")).andReturn()

        assertThat(ok1.response.status).isEqualTo(200)
        assertThat(ok2.response.status).isEqualTo(200)
        assertThat(limited.response.status).isEqualTo(429)
    }

    companion object {
        private lateinit var tempDir: Path

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            tempDir = Files.createTempDirectory("streamsheet-rate-limit-test")
            registry.add("streamsheet.storage.local.path") { tempDir.toString() }
        }
    }
}
