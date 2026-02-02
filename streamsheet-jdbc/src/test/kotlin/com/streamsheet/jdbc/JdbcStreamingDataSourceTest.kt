package com.streamsheet.jdbc

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import java.lang.reflect.Field

class JdbcStreamingDataSourceTest {

    private lateinit var db: EmbeddedDatabase
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        db = EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("classpath:schema.sql")
            .build()
        jdbcTemplate = JdbcTemplate(db)
        
        // 데이터 삽입
        for (i in 1..100) {
            jdbcTemplate.update("INSERT INTO test_data (name, data_value) VALUES (?, ?)", "Name-$i", i)
        }
    }

    @AfterEach
    fun tearDown() {
        db.shutdown()
    }

    @Test
    @DisplayName("SQL 쿼리 결과를 스트리밍으로 읽어올 수 있어야 한다")
    fun `should stream result set from SQL`() {
        // Given
        val sql = "SELECT * FROM test_data"
        val rowMapper = { rs: java.sql.ResultSet, _: Int -> rs.getInt("data_value") }
        val dataSource = JdbcStreamingDataSource(jdbcTemplate, sql, rowMapper)

        // When
        val result = dataSource.stream().toList()

        // Then
        assertEquals(100, result.size)
        assertEquals(1, result[0])
        assertEquals(100, result[99])
        
        dataSource.close()
    }

    @Test
    @DisplayName("Named Parameter가 포함된 쿼리를 스트리밍할 수 있어야 한다")
    fun `should stream with named parameters`() {
        // Given
        val sql = "SELECT * FROM test_data WHERE data_value > :minVal"
        val params = mapOf("minVal" to 50)
        val rowMapper = { rs: java.sql.ResultSet, _: Int -> rs.getInt("data_value") }
        val dataSource = JdbcStreamingDataSource(jdbcTemplate, sql, rowMapper, params)

        // When
        val result = dataSource.stream().toList()

        // Then
        assertEquals(50, result.size)
        assertEquals(51, result[0])
        assertEquals(100, result[49])
        
        dataSource.close()
    }

    @Test
    @DisplayName("DataSource가 없는 JdbcTemplate 사용 시 예외가 발생해야 한다")
    fun `should throw exception when DataSource is missing`() {
        val emptyTemplate = JdbcTemplate()
        val sql = "SELECT 1"
        val rowMapper = { rs: java.sql.ResultSet, _: Int -> 1 }
        
        val exception = assertThrows<IllegalArgumentException> {
            JdbcStreamingDataSource(emptyTemplate, sql, rowMapper, fetchSize = 100)
        }
        assertTrue(exception.message!!.contains("JdbcTemplate must have a valid DataSource"))
    }

    @Test
    @DisplayName("스트림이 닫히면 activeStreams에서 제거되어야 한다")
    fun `should remove stream from activeStreams when closed`() {
        // Given
        val sql = "SELECT * FROM test_data"
        val rowMapper = { rs: java.sql.ResultSet, _: Int -> rs.getInt("data_value") }
        val dataSource = JdbcStreamingDataSource(jdbcTemplate, sql, rowMapper)

        // When
        val sequence = dataSource.stream()
        
        // Reflection to check private field
        val field: Field = dataSource.javaClass.getDeclaredField("activeStreams")
        field.isAccessible = true
        val activeStreams = field.get(dataSource) as List<*>
        
        assertEquals(1, activeStreams.size)

        // 스트림 소비 후 닫기 (Sequence의 경우 명시적으로 닫을 방법이 제한적이니 원본 Stream을 닫아야 함)
        // 실제 운영 환경에서는 AutoCloseable이나 stream.close() 호출 시 동작함
        dataSource.close()
        
        // Then
        assertEquals(0, activeStreams.size)
    }
    @Test
    @DisplayName("스트림이 완전히 소비되면 자동으로 activeStreams에서 제거되어야 한다")
    fun `should auto-close stream when fully consumed`() {
        // Given
        val sql = "SELECT * FROM test_data"
        val rowMapper = { rs: java.sql.ResultSet, _: Int -> rs.getInt("data_value") }
        val dataSource = JdbcStreamingDataSource(jdbcTemplate, sql, rowMapper)

        // When
        dataSource.stream().toList() // Full consumption

        // Then
        val field: Field = dataSource.javaClass.getDeclaredField("activeStreams")
        field.isAccessible = true
        val activeStreams = field.get(dataSource) as List<*>
        
        assertEquals(0, activeStreams.size, "Should be empty without explicit close() call if fully consumed")
    }

    @Test
    @DisplayName("부분 소비 후 close 호출 시 리소스가 정리되어야 한다")
    fun `should cleanup resources when closed after partial consumption`() {
        // Given
        val sql = "SELECT * FROM test_data"
        val rowMapper = { rs: java.sql.ResultSet, _: Int -> rs.getInt("data_value") }
        val dataSource = JdbcStreamingDataSource(jdbcTemplate, sql, rowMapper)

        // When
        dataSource.stream().take(1).toList() // Partial consumption

        // 부분 소비 시에는 스트림이 아직 닫히지 않은 상태여야 함 (끝까지 읽지 않았으므로)
        val field: Field = dataSource.javaClass.getDeclaredField("activeStreams")
        field.isAccessible = true
        val activeStreamsBeforeClose = field.get(dataSource) as List<*>
        
        // 명확히 1개가 남아있어야 정상
        assertEquals(1, activeStreamsBeforeClose.size, "Stream should remain active if not fully consumed")
        
        dataSource.close()
        
        // Then
        val activeStreamsAfterClose = field.get(dataSource) as List<*>
        assertEquals(0, activeStreamsAfterClose.size, "Should be empty after close()")
    }
}
