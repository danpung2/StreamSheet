package com.streamsheet.core.schema

/**
 * DSL 기반 엑셀 스키마 빌더
 * DSL-based Excel schema builder
 *
 * NOTE: 람다 DSL을 사용하여 유연하게 스키마를 정의할 수 있습니다.
 * Allows flexible schema definition using lambda DSL.
 *
 * @param T 엔티티 타입 / Entity type
 */
class DslExcelSchema<T>(
    override val sheetName: String,
    private val columns: List<ColumnDefinition<T>>
) : ExcelSchema<T> {

    override val headers: List<String>
        get() = columns.map { it.header }

    override val columnWidths: List<Int>
        get() = columns.map { it.width }

    override fun toRow(entity: T): List<Any?> {
        return columns.map { it.extractor(entity) }
    }

    /**
     * 컬럼 정의 / Column definition
     */
    data class ColumnDefinition<T>(
        val header: String,
        val width: Int,
        val extractor: (T) -> Any?
    )
}

/**
 * 스키마 빌더 DSL
 * Schema builder DSL
 */
class ExcelSchemaBuilder<T> {
    var sheetName: String = "Sheet1"
    private val columns = mutableListOf<DslExcelSchema.ColumnDefinition<T>>()

    /**
     * 컬럼 추가 / Add column
     *
     * @param header 헤더명 / Header name
     * @param width 컬럼 너비 / Column width
     * @param extractor 값 추출 함수 / Value extractor function
     */
    fun column(header: String, width: Int = 15, extractor: (T) -> Any?) {
        columns.add(DslExcelSchema.ColumnDefinition(header, width, extractor))
    }

    /**
     * 스키마 빌드 / Build schema
     */
    fun build(): DslExcelSchema<T> {
        return DslExcelSchema(sheetName, columns.toList())
    }
}

/**
 * DSL 진입점 함수 / DSL entry point function
 *
 * 사용 예시 / Usage example:
 * ```kotlin
 * val schema = excelSchema<Order> {
 *     sheetName = "주문 목록"
 *     column("주문번호", 20) { it.orderId }
 *     column("고객명", 15) { it.customerName }
 *     column("금액", 15) { it.amount }
 * }
 * ```
 */
fun <T> excelSchema(block: ExcelSchemaBuilder<T>.() -> Unit): DslExcelSchema<T> {
    return ExcelSchemaBuilder<T>().apply(block).build()
}
