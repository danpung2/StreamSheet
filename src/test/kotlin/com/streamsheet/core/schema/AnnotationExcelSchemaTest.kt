package com.streamsheet.core.schema

import com.streamsheet.core.annotation.ExcelColumn
import com.streamsheet.core.annotation.ExcelSheet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("어노테이션 기반 엑셀 스키마 테스트")
class AnnotationExcelSchemaTest {

    @ExcelSheet(name = "Test Sheet")
    data class TestEntity(
        @ExcelColumn(header = "ID", width = 10, order = 1)
        val id: Long,
        
        @ExcelColumn(header = "Name", width = 20, order = 2)
        val name: String,
        
        val ignored: String = "ignored"
    )

    @Test
    @DisplayName("어노테이션 정보로부터 스키마를 생성해야 한다")
    fun `should create schema from annotation`() {
        val schema = AnnotationExcelSchema.create<TestEntity>()
        
        assertEquals("Test Sheet", schema.sheetName)
        assertEquals(listOf("ID", "Name"), schema.headers)
        assertEquals(listOf(10, 20), schema.columnWidths)
    }

    @Test
    @DisplayName("엔티티에서 값을 정확히 추출해야 한다")
    fun `should extract values correctly`() {
        val schema = AnnotationExcelSchema.from(TestEntity::class)
        val entity = TestEntity(1L, "Test Name")
        
        val row = schema.toRow(entity)
        
        assertEquals(listOf(1L, "Test Name"), row)
    }
}
