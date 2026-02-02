package com.streamsheet.core.exporter

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.schema.excelSchema
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("SXSSF 엑셀 내보내기 동시성 테스트")
class SxssfExcelExporterConcurrencyTest {

    data class TestEntity(val name: String, val value: Int)

    class SimpleTestDataSource(private val size: Int) : StreamingDataSource<TestEntity> {
        override fun stream(): Sequence<TestEntity> = (1..size).asSequence().map { TestEntity("User $it", it) }
        override fun close() {}
    }

    @Test
    @DisplayName("공유된 Exporter 인스턴스로 동시 내보내기가 가능해야 한다")
    fun `should support concurrent exports with shared instance`() {
        val threadCount = 8
        val service = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)
        
        val exporter = SxssfExcelExporter()
        val schema = excelSchema<TestEntity> {
            column("Name") { it.name }
            column("Value") { it.value }
        }

        for (i in 0 until threadCount) {
            service.submit {
                try {
                    startLatch.await() // Wait for signal
                    val output = ByteArrayOutputStream()
                    // Each thread gets its own data source and output stream
                    val dataSource = SimpleTestDataSource(100) 
                    exporter.export(schema, dataSource, output, ExcelExportConfig())
                    
                    if (output.size() == 0) {
                        throw RuntimeException("Output is empty")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        // Start all threads at once
        startLatch.countDown()
        
        // Wait for completion
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        service.shutdownNow()

        assertTrue(finished, "Test timed out")
        assertEquals(0, errorCount.get(), "Should have 0 errors during concurrent export")
    }

    @Test
    @DisplayName("스레드별 개별 Exporter 인스턴스로 동시 내보내기가 가능해야 한다")
    fun `should support concurrent exports with separate instances`() {
        val threadCount = 8
        val service = Executors.newFixedThreadPool(threadCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)
        
        val schema = excelSchema<TestEntity> {
            column("Name") { it.name }
            column("Value") { it.value }
        }

        for (i in 0 until threadCount) {
            service.submit {
                try {
                    startLatch.await()
                    val output = ByteArrayOutputStream()
                    val dataSource = SimpleTestDataSource(100)
                    // New exporter per thread
                    val exporter = SxssfExcelExporter()
                    exporter.export(schema, dataSource, output, ExcelExportConfig())
                    
                    if (output.size() == 0) {
                        throw RuntimeException("Output is empty")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        val finished = doneLatch.await(10, TimeUnit.SECONDS)
        service.shutdownNow()

        assertTrue(finished, "Test timed out")
        assertEquals(0, errorCount.get(), "Should have 0 errors during concurrent export")
    }
}
