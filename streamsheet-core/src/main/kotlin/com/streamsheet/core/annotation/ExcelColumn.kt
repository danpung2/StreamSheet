package com.streamsheet.core.annotation

/**
 * 엑셀 컬럼 정의 어노테이션
 * Excel column definition annotation
 *
 * NOTE: 이 어노테이션을 프로퍼티에 적용하여 컬럼 헤더와 너비를 정의합니다.
 * Apply this annotation to a property to define column header and width.
 *
 * @param header 컬럼 헤더명 / Column header name
 * @param width 컬럼 너비 (문자 단위) / Column width (in characters)
 * @param order 컬럼 순서 (낮을수록 왼쪽) / Column order (lower = left)
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ExcelColumn(
    val header: String,
    val width: Int = 15,
    val order: Int = Int.MAX_VALUE,
    val pattern: String = ""
)
