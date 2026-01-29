package com.streamsheet.jdbc

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType

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
}
