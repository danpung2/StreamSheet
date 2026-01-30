package com.streamsheet.core;

import com.streamsheet.core.config.ExcelExportConfig;
import com.streamsheet.core.schema.AnnotationExcelSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaInteropTest {

    @Test
    void excelExportConfigBuilderShouldWork() {
        ExcelExportConfig config = ExcelExportConfig.builder()
            .rowAccessWindowSize(50)
            .flushBatchSize(10)
            .preventFormulaInjection(true)
            .build();

        assertEquals(50, config.getRowAccessWindowSize());
        assertEquals(10, config.getFlushBatchSize());
    }

    @Test
    void annotationSchemaFactoryShouldAcceptJavaClass() {
        AnnotationExcelSchema<JavaInteropRow> schema = AnnotationExcelSchema.from(JavaInteropRow.class);
        assertNotNull(schema);
    }
}
