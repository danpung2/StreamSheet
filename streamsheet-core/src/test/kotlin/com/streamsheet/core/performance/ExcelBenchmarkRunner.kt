package com.streamsheet.core.performance

import com.streamsheet.core.config.ExcelExportConfig
import com.streamsheet.core.datasource.StreamingDataSource
import com.streamsheet.core.exporter.SxssfExcelExporter
import com.streamsheet.core.schema.excelSchema
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.util.concurrent.atomic.AtomicLong

/**
 * 대규모 데이터 성능 벤치마크 러너
 * Large-scale data performance benchmark runner
 */
class ExcelBenchmarkRunner {
    private val logger = LoggerFactory.getLogger(ExcelBenchmarkRunner::class.java)
    private val memoryBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()

    data class BenchmarkEntity(
        val id: Long,
        val name: String,
        val email: String,
        val amount: Double,
        val description: String
    )

    class PerformanceDataSource(private val totalRows: Long) : StreamingDataSource<BenchmarkEntity> {
        override fun stream(): Sequence<BenchmarkEntity> = (1..totalRows).asSequence().map { i ->
            BenchmarkEntity(
                id = i,
                name = "User_$i",
                email = "user_$i@example.com",
                amount = i * 1.5,
                description = "This is a detailed description for row $i to increase memory footprint."
            )
        }
        override fun close() {}
    }

    /**
     * 특정 행 수에 대한 벤치마크 실행
     * Run benchmark for specific row count
     * @param useRealIo If true, writes to a real temporary file to measure disk I/O performance.
     */
    fun run(rowCount: Long, config: ExcelExportConfig = ExcelExportConfig.DEFAULT, useRealIo: Boolean = false): BenchmarkResult {
        logger.info("Starting benchmark for {} rows (Real IO: {})...", rowCount, useRealIo)
        
        // GC 강제 호출로 깨끗한 상태에서 시작 (권장되지는 않으나 벤치마크용)
        System.gc()
        val startMemory = memoryBean.heapMemoryUsage.used
        val startTime = System.currentTimeMillis()
        
        val schema = excelSchema<BenchmarkEntity> {
            sheetName = "Benchmark"
            column("ID") { it.id }
            column("Name") { it.name }
            column("Email") { it.email }
            column("Amount") { it.amount }
            column("Description") { it.description }
        }

        val dataSource = PerformanceDataSource(rowCount)
        val exporter = SxssfExcelExporter()
        
        val outputStream = if (useRealIo) {
            val tempFile = java.io.File.createTempFile("benchmark_$rowCount", ".xlsx")
            tempFile.deleteOnExit() // JVM 종료 시 삭제 (테스트 후 즉시 삭제는 finally에서 처리 가능)
            logger.info("Writing to temporary file: ${tempFile.absolutePath}")
            java.io.FileOutputStream(tempFile)
        } else {
            // NullOutputStream: 실제 파일 I/O 부하를 제외하고 엔진 순수 성능 측정
            object : OutputStream() {
                private val bytesWritten = AtomicLong(0)
                override fun write(b: Int) { bytesWritten.incrementAndGet() }
                override fun write(b: ByteArray, off: Int, len: Int) { bytesWritten.addAndGet(len.toLong()) }
            }
        }

        var peakMemory = startMemory
        
        // 간단한 메모리 모니터링 스레드 (샘플링)
        val monitorThread = Thread {
            try {
                while (!Thread.currentThread().isInterrupted) {
                    val current = memoryBean.heapMemoryUsage.used
                    if (current > peakMemory) peakMemory = current
                    Thread.sleep(100)
                }
            } catch (e: InterruptedException) { }
        }
        monitorThread.start()

        try {
            outputStream.use { os ->
                exporter.export(schema, dataSource, os, config)
            }
        } finally {
            monitorThread.interrupt()
            monitorThread.join()
        }

        val endTime = System.currentTimeMillis()
        val durationMs = endTime - startTime
        val memoryUsedMb = (peakMemory - startMemory) / (1024.0 * 1024.0)

        val result = BenchmarkResult(rowCount, durationMs, memoryUsedMb)
        println("\n[BENCHMARK] $result (Real IO: $useRealIo)")
        
        return result
    }

    data class BenchmarkResult(
        val rowCount: Long,
        val durationMs: Long,
        val peakMemoryUsageMb: Double
    ) {
        override fun toString(): String {
            return String.format(
                "Rows: %7d | Time: %6d ms | TPS: %6.0f rows/s | Peak Memory: %6.2f MB",
                rowCount, durationMs, (rowCount.toDouble() / durationMs * 1000), peakMemoryUsageMb
            )
        }
    }
}
