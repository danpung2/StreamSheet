package com.streamsheet.core.schema

import com.streamsheet.core.annotation.ExcelColumn
import com.streamsheet.core.annotation.ExcelSheet
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine

/**
 * 어노테이션 기반 엑셀 스키마 구현 (캐싱 지원)
 * Annotation-based Excel schema implementation (with caching)
 *
 * NOTE: @ExcelSheet, @ExcelColumn 어노테이션을 읽어 자동으로 스키마를 생성합니다.
 * 클래스별 스키마 정보를 캐싱하여 리플렉션 비용을 절감합니다.
 *
 * @param T 엔티티 타입 / Entity type
 * @param kClass 대상 클래스 / Target class
 */
class AnnotationExcelSchema<T : Any>(
    private val kClass: KClass<T>
) : ExcelSchema<T> {

    /**
     * 컬럼 메타데이터 / Column metadata
     */
    private data class ColumnMeta(
        val property: KProperty1<*, *>,
        val header: String,
        val width: Int,
        val order: Int,
        val pattern: String?
    )

    /**
     * 정렬된 컬럼 메타데이터 목록 / Sorted column metadata list
     */
    private val columns: List<ColumnMeta> by lazy {
        kClass.memberProperties
            .mapNotNull { prop ->
                prop.findAnnotation<ExcelColumn>()?.let { annotation ->
                    ColumnMeta(
                        property = prop,
                        header = annotation.header,
                        width = annotation.width,
                        order = annotation.order,
                        pattern = annotation.pattern.ifBlank { null }
                    )
                }
            }
            .sortedBy { it.order }
    }

    override val sheetName: String by lazy {
        kClass.findAnnotation<ExcelSheet>()?.name ?: kClass.simpleName ?: "Sheet1"
    }

    override val headers: List<String> by lazy {
        columns.map { it.header }
    }

    override val columnWidths: List<Int> by lazy {
        columns.map { it.width }
    }

    override val columnPatterns: List<String?> by lazy {
        columns.map { it.pattern }
    }

    // NOTE: 제네릭 타입 소거(Type Erasure)로 인한 형변환 경고를 억제합니다.
    // Suppress unchecked cast warnings due to type erasure.
    @Suppress("UNCHECKED_CAST")
    override fun toRow(entity: T): List<Any?> {
        return columns.map { meta ->
            (meta.property as KProperty1<T, *>).get(entity)
        }
    }

    companion object {
        /**
         * 스키마 캐시 (Caffeine) / Schema cache
         *
         * NOTE: ConcurrentHashMap 대신 Caffeine을 사용하여 메모리 누수를 방지합니다.
         * Replaced ConcurrentHashMap with Caffeine to prevent memory leaks.
         * - Maximum Size: 1000
         * - Eviction: 1 hour after access
         */
        private val cache: Cache<KClass<*>, AnnotationExcelSchema<*>> = 
            Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(1, java.util.concurrent.TimeUnit.HOURS)
                .recordStats()
                .build()

        /**
         * 클래스로부터 스키마 생성 (캐싱 적용)
         *
         * @param T 엔티티 타입 / Entity type
         * @param kClass 대상 클래스 / Target class
         * @return 어노테이션 기반 스키마 / Annotation-based schema
         */
        // NOTE: 제네릭 타입 소거(Type Erasure)로 인한 형변환 경고를 억제합니다.
        // Suppress unchecked cast warnings due to type erasure.
        @Suppress("UNCHECKED_CAST")
        fun <T : Any> from(kClass: KClass<T>): AnnotationExcelSchema<T> {
            return cache.get(kClass) { clazz ->
                AnnotationExcelSchema(clazz as KClass<T>)
            } as AnnotationExcelSchema<T>
        }

        @JvmStatic
        fun <T : Any> from(clazz: Class<T>): AnnotationExcelSchema<T> {
            return from(clazz.kotlin)
        }

        /**
         * 인라인 reified 버전 (캐싱 적용)
         */
        inline fun <reified T : Any> create(): AnnotationExcelSchema<T> {
            return from(T::class)
        }
    }
}
