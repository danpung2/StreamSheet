package com.streamsheet.mongodb

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.exporter.SxssfExcelExporter
import com.streamsheet.core.schema.excelSchema
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import java.io.File
import java.io.FileOutputStream

@Document(collection = "mongo_benchmark")
data class MongoBenchmarkEntity(
    @Id
    val id: String? = null,
    val name: String,
    val amount: Double,
    val description: String
)

@DataMongoTest
@DisplayName("Mongo Data Source 통합 벤치마크 (Testcontainers)")
class MongoBenchmarkTest {

    companion object {
        @org.springframework.boot.testcontainers.service.connection.ServiceConnection
        @org.testcontainers.junit.jupiter.Container
        val mongoDBContainer = org.testcontainers.containers.MongoDBContainer("mongo:6.0")
            .withReuse(true)

        init {
             mongoDBContainer.start()
        }
    }

    @Autowired
    private lateinit var mongoTemplate: MongoTemplate

    @Test
    @DisplayName("Mongo -> Stream -> Excel 실제 I/O 벤치마크 (10만 건)")
    fun `benchmark_mongo_export_real_io`() {
        val rowCount = 100_000
        println("Inserting $rowCount documents into MongoDB Container...")

        // 1. Data Prep (Batch Insert)
        val batchSize = 5000
        val documents = ArrayList<MongoBenchmarkEntity>(batchSize)
        
        for (i in 1..rowCount) {
            documents.add(
                MongoBenchmarkEntity(
                    id = "id_$i",
                    name = "User_$i",
                    amount = i * 1.5,
                    description = "Description for User $i with Mongo specific testing payload."
                )
            )
            if (documents.size == batchSize) {
                mongoTemplate.insertAll(documents)
                documents.clear()
            }
        }
        if (documents.isNotEmpty()) {
            mongoTemplate.insertAll(documents)
        }
        println("Data insertion completed.")

        // 2. Setup
        val dataSource = MongoStreamingDataSource.create<MongoBenchmarkEntity>(mongoTemplate)

        val schema = excelSchema<MongoBenchmarkEntity> {
            sheetName = "Mongo Benchmark"
            column("ID") { it.id }
            column("Name") { it.name }
            column("Amount") { it.amount }
            column("Description") { it.description }
        }

        val exporter = SxssfExcelExporter()
        val tempFile = File.createTempFile("mongo_benchmark", ".xlsx")
        tempFile.deleteOnExit()

        // 3. Execution
        println("Starting Mongo Export Pipeline...")
        val startTime = System.currentTimeMillis()

        FileOutputStream(tempFile).use { os ->
            exporter.export(schema, dataSource, os, ExcelExportConfig.DEFAULT)
        }

        val endTime = System.currentTimeMillis()
        val durationMs = endTime - startTime

        // 4. Report
        val fileSizeMb = tempFile.length() / (1024.0 * 1024.0)
        println("\n[MONGO BENCHMARK RESULT]")
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

