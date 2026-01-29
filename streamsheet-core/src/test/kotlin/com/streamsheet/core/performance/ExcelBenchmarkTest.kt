package com.streamsheet.core.performance

import com.streamsheet.core.config.ExcelExportConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Excel 성능 벤치마크 테스트")
class ExcelBenchmarkTest {

    private val runner = ExcelBenchmarkRunner()

    @Test
    @DisplayName("10만 건 데이터 내보내기 성능 테스트")
    fun benchmark100k() {
        runner.run(100_000, ExcelExportConfig.DEFAULT)
    }

    @Test
    @DisplayName("50만 건 데이터 내보내기 성능 테스트")
    fun benchmark500k() {
        runner.run(500_000, ExcelExportConfig.HIGH_PERFORMANCE)
    }

    @Test
    @DisplayName("100만 건 데이터 내보내기 성능 테스트 (엑셀 한계치)")
    fun benchmark1M() {
        // 100만 건은 힙 사이즈가 충분해야 함 (최소 512MB 권장)
        runner.run(1_000_000, ExcelExportConfig.HIGH_PERFORMANCE)
    }
    @Test
    @DisplayName("10만 건 데이터 실제 디스크 쓰기 성능 테스트 (Write to Disk)")
    fun benchmark100kWithRealIo() {
        runner.run(100_000, ExcelExportConfig.DEFAULT, useRealIo = true)
    }
}
