package com.streamsheet.core.annotation

/**
 * 엑셀 시트 정의 어노테이션
 * Excel sheet definition annotation
 *
 * NOTE: 이 어노테이션을 DTO 클래스에 적용하여 시트 이름을 정의합니다.
 * Apply this annotation to a DTO class to define the sheet name.
 *
 * @param name 시트 이름 / Sheet name
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExcelSheet(
    val name: String = "Sheet1"
)
