package com.streamsheet.core.schema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("DSL 기반 엑셀 스키마 테스트")
class DslExcelSchemaTest {

    data class TestEntity(
        val id: Long,
        val name: String
    )

    @Test
    @DisplayName("DSL을 사용하여 스키마를 생성해야 한다")
    fun `should create schema using dsl`() {
        val schema = excelSchema<TestEntity> {
            sheetName = "DSL Sheet"
            column("ID", 10) { it.id }
            column("Name", 20) { it.name }
        }

        assertEquals("DSL Sheet", schema.sheetName)
        assertEquals(listOf("ID", "Name"), schema.headers)
        assertEquals(listOf(10, 20), schema.columnWidths)
    }

    @Test
    @DisplayName("DSL 스키마를 사용하여 값을 추출해야 한다")
    fun `should extract values using dsl schema`() {
        val schema = excelSchema<TestEntity> {
            column("ID") { it.id }
            column("Name") { it.name }
        }
        val entity = TestEntity(1L, "Test Name")

        val row = schema.toRow(entity)

        assertEquals(listOf(1L, "Test Name"), row)
    }
}
