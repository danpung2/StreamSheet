package com.streamsheet.jdbc

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.exporter.SxssfExcelExporter
import com.streamsheet.core.schema.excelSchema
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

@DisplayName("JDBC Data Source 통합 벤치마크")
class JdbcBenchmarkTest {

    private lateinit var db: EmbeddedDatabase
    private lateinit var jdbcTemplate: JdbcTemplate

    data class BenchmarkEntity(
        val id: Int,
        val name: String,
        val description: String
    )

    @BeforeEach
    fun setUp() {
        // H2 DB Setup (In-Memory but accessed via JDBC interface)
        db = EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .addScript("classpath:schema.sql")
            .build()
        jdbcTemplate = JdbcTemplate(db)
    }

    @AfterEach
    fun tearDown() {
        db.shutdown()
    }

    @Test
    @DisplayName("DB(H2) -> Stream -> Excel 파일 저장 전체 파이프라인 성능 측정 (10만 건)")
    fun `benchmark_full_pipeline_with_real_db_and_io`() {
        // 1. Data Preparation (100k rows)
        val rowCount = 100_000
        println("Inserting $rowCount rows into H2 DB...")
        
        // Batch Insert for speed in setup
        val batchSize = 1000
        val sql = "INSERT INTO test_data (name, data_value) VALUES (?, ?)"
        
        val batchArgs = ArrayList<Array<Any>>(batchSize)
        for (i in 1..rowCount) {
            batchArgs.add(arrayOf("Name-$i", i))
            if (i % batchSize == 0) {
                jdbcTemplate.batchUpdate(sql, batchArgs)
                batchArgs.clear()
            }
        }
        if (batchArgs.isNotEmpty()) {
            jdbcTemplate.batchUpdate(sql, batchArgs)
        }
        println("Data insertion completed.")

        // 2. Setup Components
        val exportSql = "SELECT * FROM test_data"
        val rowMapper = { rs: java.sql.ResultSet, _: Int -> 
            BenchmarkEntity(
                id = rs.getInt("id"), // Assuming implicit ID if schema.sql defines it, otherwise using row num or just data
                name = rs.getString("name"),
                description = "Description for " + rs.getInt("data_value")
            )
        }
        
        // We need to check schema.sql to see if 'id' exists. 
        // Based on previous test view, column 'data_value' exists.
        // Let's just map data_value to id for simplicity if id column isn't guaranteed.
        // Re-reading previous test: "INSERT INTO test_data (name, data_value) VALUES (?, ?)"
        // If schema.sql has ID auto-increment, it's fine. 
        // Let's assume standard 'id' or use row num if needed.
        // Safe RowMapper:
        val safeRowMapper = { rs: java.sql.ResultSet, _: Int ->
            BenchmarkEntity(
                id = rs.getInt("data_value"), // Use data_value as ID equivalent
                name = rs.getString("name"),
                description = "This is a description for ${rs.getString("name")}"
            )
        }

        // Use fetchSize to test streaming capabilities properly
        val dataSource = JdbcStreamingDataSource(
            jdbcTemplate, 
            exportSql, 
            safeRowMapper, 
            fetchSize = 1000
        )

        val schema = excelSchema<BenchmarkEntity> {
            sheetName = "DB Benchmark"
            column("ID") { it.id }
            column("Name") { it.name }
            column("Description") { it.description }
        }

        val exporter = SxssfExcelExporter()
        val tempFile = File.createTempFile("jdbc_benchmark", ".xlsx")
        tempFile.deleteOnExit()

        // 3. Execution & Measurement
        println("Starting Export Pipeline...")
        val startTime = System.currentTimeMillis()
        
        FileOutputStream(tempFile).use { os ->
            exporter.export(schema, dataSource, os, ExcelExportConfig.DEFAULT)
        }
        
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - startTime
        
        // 4. Report
        val fileSizeMb = tempFile.length() / (1024.0 * 1024.0)
        println("\n[JDBC BENCHMARK RESULT]")
        println("Rows: $rowCount")
        println("Time: $durationMs ms")
        println("File Size: %.2f MB".format(fileSizeMb))
        println("TPS: %.0f rows/s".format(rowCount / (durationMs / 1000.0)))
        println("Temp File: ${tempFile.absolutePath}")
        
        dataSource.close()
    }
}
