package com.streamsheet.jpa

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.exporter.SxssfExcelExporter
import com.streamsheet.core.schema.excelSchema
import jakarta.persistence.*
import org.springframework.data.jpa.repository.QueryHints
import jakarta.persistence.QueryHint
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.io.File
import java.io.FileOutputStream
import java.util.stream.Stream
import java.util.concurrent.atomic.AtomicInteger
import org.hibernate.jpa.HibernateHints

@Entity
@Table(name = "jpa_benchmark_entity")
data class JpaBenchmarkEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val name: String,
    val amount: Long,
    @Column(length = 200)
    val description: String
)

@Repository
interface JpaBenchmarkRepository : JpaRepository<JpaBenchmarkEntity, Long> {
    @QueryHints(value = [
        QueryHint(name = HibernateHints.HINT_FETCH_SIZE, value = "1000"),
        QueryHint(name = HibernateHints.HINT_CACHEABLE, value = "false"),
        QueryHint(name = HibernateHints.HINT_READ_ONLY, value = "true")
    ])
    @Query("SELECT j FROM JpaBenchmarkEntity j")
    fun streamAll(): Stream<JpaBenchmarkEntity>
}

@DataJpaTest
@DisplayName("JPA Data Source 통합 벤치마크")
class JpaBenchmarkTest {

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var repository: JpaBenchmarkRepository

    @Test
    @DisplayName("JPA -> Stream -> Excel 실제 I/O 벤치마크 (10만 건)")
    fun `benchmark_jpa_export_real_io`() {
        val rowCount = 100_000
        println("Inserting $rowCount rows into JPA H2 DB (Batch)...")

        // 1. Data Prep (Batch Insert)
        val batchSize = 1000
        val entities = ArrayList<JpaBenchmarkEntity>(batchSize)
        for (i in 1..rowCount) {
            entities.add(
                JpaBenchmarkEntity(
                    name = "User_$i",
                    amount = i * 10L,
                    description = "Description for User $i with some padding text to increase payload size."
                )
            )
            if (entities.size == batchSize) {
                repository.saveAll(entities)
                entityManager.flush()
                entityManager.clear()
                entities.clear()
            }
        }
        if (entities.isNotEmpty()) {
            repository.saveAll(entities)
            entityManager.flush()
            entityManager.clear()
        }
        println("Data insertion completed.")
        
        // 2. Setup
        // detachEntities=true로 설정하여 영속성 컨텍스트 오버헤드를 최소화하고 스트리밍 성능 극대화
        val dataSource = JpaStreamingDataSource(
            entityManager = entityManager,
            streamProvider = { repository.streamAll() },
            detachEntities = true 
        )

        val schema = excelSchema<JpaBenchmarkEntity> {
            sheetName = "JPA Benchmark"
            column("ID") { it.id }
            column("Name") { it.name }
            column("Amount") { it.amount }
            column("Description") { it.description }
        }

        val exporter = SxssfExcelExporter()
        val tempFile = File.createTempFile("jpa_benchmark", ".xlsx")
        tempFile.deleteOnExit()
        
        // 3. Execution
        println("Starting JPA Export Pipeline...")
        val startTime = System.currentTimeMillis()
        
        FileOutputStream(tempFile).use { os ->
            exporter.export(schema, dataSource, os, ExcelExportConfig.DEFAULT)
        }
        
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - startTime
        
        // 4. Report
        val fileSizeMb = tempFile.length() / (1024.0 * 1024.0)
        println("\n[JPA BENCHMARK RESULT]")
        println("Rows: $rowCount")
        println("Time: $durationMs ms")
        println("File Size: %.2f MB".format(fileSizeMb))
        println("TPS: %.0f rows/s".format(rowCount / (durationMs / 1000.0)))
        println("Temp File: ${tempFile.absolutePath}")
        
        assertTrue(durationMs > 0)
        assertTrue(tempFile.exists())
        assertTrue(tempFile.length() > 0)
        
        dataSource.close()
    }
}
