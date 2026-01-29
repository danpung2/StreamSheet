# StreamSheet

> 대용량 데이터를 위한 스트리밍 엑셀 내보내기 SDK  

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![License](https://img.shields.io/badge/license-MIT-blue)]()

## 개요

StreamSheet은 Apache POI SXSSF를 기반으로 한 **대용량 엑셀 내보내기 SDK**입니다. 20만 건 이상의 데이터도 메모리 효율적으로 처리하며, 어노테이션 또는 DSL 기반의 간편한 스키마 정의를 제공합니다.

## 주요 기능

- ✅ **메모리 효율성**: SXSSF 기반 스트리밍으로 OOM 방지
- ✅ **제네릭 설계**: 어떤 데이터 타입도 처리 가능
- ✅ **어노테이션 기반 스키마**: `@ExcelSheet`, `@ExcelColumn`으로 간편 정의
- ✅ **DSL 기반 스키마**: 람다 DSL로 유연한 스키마 구성
- ✅ **MongoDB 통합**: 커서 기반 스트리밍 데이터 소스 내장

## 빠른 시작

### 1. 의존성 추가

```gradle
dependencies {
    implementation 'io.github.danpung2:streamsheet-core:0.0.1-SNAPSHOT'
}
```

### 2. 어노테이션 기반 사용

```kotlin
// 1. DTO 정의
@ExcelSheet(name = "주문 목록")
data class OrderExcelDto(
    @ExcelColumn(header = "주문번호", width = 20, order = 1)
    val orderId: String,

    @ExcelColumn(header = "고객명", width = 15, order = 2)
    val customerName: String,

    @ExcelColumn(header = "금액", width = 15, order = 3)
    val amount: Long
)

// 2. 스키마 생성
val schema = AnnotationExcelSchema.create<OrderExcelDto>()

// 3. 내보내기 실행
val exporter = SxssfExcelExporter()
exporter.export(schema, dataSource, outputStream)
```

### 3. DSL 기반 사용

```kotlin
val schema = excelSchema<Order> {
    sheetName = "주문 목록"
    column("주문번호", 20) { it.orderId }
    column("고객명", 15) { it.customerName }
    column("금액", 15) { it.amount.toString() }
}

exporter.export(schema, dataSource, outputStream)
```

## 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    ExcelExporter                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ ExcelSchema │  │ DataSource  │  │ ExcelExportConfig   │  │
│  │ - headers   │  │ - stream()  │  │ - windowSize        │  │
│  │ - widths    │  │ - filter()  │  │ - flushBatchSize    │  │
│  │ - toRow()   │  │             │  │ - compressTempFiles │  │
│  └──────┬──────┘  └──────┬──────┘  └─────────┬───────────┘  │
│         │                │                   │              │
│         └────────────────┼───────────────────┘              │
│                          ▼                                  │
│               ┌─────────────────────┐                       │
│               │  SXSSFWorkbook      │                       │
│               │  (Apache POI)       │                       │
│               └──────────┬──────────┘                       │
│                          ▼                                  │
│               ┌─────────────────────┐                       │
│               │  OutputStream       │                       │
│               │  (HTTP Response)    │                       │
│               └─────────────────────┘                       │
└─────────────────────────────────────────────────────────────┘
```

## 핵심 인터페이스

### ExcelSchema<T>
```kotlin
interface ExcelSchema<T> {
    val sheetName: String
    val headers: List<String>
    val columnWidths: List<Int>
    fun toRow(entity: T): List<Any?>
}
```

### StreamingDataSource<T>
```kotlin
interface StreamingDataSource<T> {
    fun stream(): Sequence<T>
    fun stream(filter: Map<String, Any>): Sequence<T>
}
```

### ExcelExporter
```kotlin
interface ExcelExporter {
    fun <T> export(
        schema: ExcelSchema<T>,
        dataSource: StreamingDataSource<T>,
        output: OutputStream,
        config: ExcelExportConfig = ExcelExportConfig.DEFAULT
    )
}
```

## 설정 옵션

```kotlin
ExcelExportConfig(
    rowAccessWindowSize = 100,  // 메모리 유지 행 수
    flushBatchSize = 1000,      // 플러시 주기
    compressTempFiles = true,   // 임시 파일 압축
    applyHeaderStyle = true,    // 헤더 스타일 적용
    applyDataBorders = true     // 데이터 테두리 적용
)

// 사전 정의된 프리셋
ExcelExportConfig.DEFAULT         // 기본 설정
ExcelExportConfig.HIGH_PERFORMANCE // 고성능 (최소 메모리)
ExcelExportConfig.HIGH_QUALITY    // 고품질 (풀 스타일링)
```

## 관련 문서

- [SDK 구현 계획](.gemini/SDK_Implementation_Plan.ko.md)
- [대용량 엑셀 내보내기 솔루션 제안서](Excel-Export-Optimized-Solution.ko.md)

## 라이선스

MIT License
