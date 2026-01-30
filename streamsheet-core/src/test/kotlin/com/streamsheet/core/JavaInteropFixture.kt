package com.streamsheet.core

import com.streamsheet.core.annotation.ExcelColumn
import com.streamsheet.core.annotation.ExcelSheet

@ExcelSheet(name = "JavaInterop")
data class JavaInteropRow(
    @ExcelColumn(header = "name", width = 10, order = 1)
    val name: String,
)
