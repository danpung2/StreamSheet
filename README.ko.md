# StreamSheet

> 대용량 데이터를 위한 메모리 효율적인 스트리밍 엑셀 내보내기 SDK

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![License](https://img.shields.io/badge/license-MIT-blue)]()

## 개요

StreamSheet은 **Apache POI SXSSF**를 기반으로 설계된 고성능 엑셀 내보내기 라이브러리입니다.
수십만 건 이상의 대용량 데이터도 OOM(OutOfMemory) 걱정 없이 안정적으로 처리할 수 있으며, 다양한 데이터 소스(JPA, JDBC, MongoDB 등)와의 통합을 지원합니다.
Kotlin DSL과 어노테이션을 통해 엑셀 스키마를 직관적이고 간편하게 정의할 수 있습니다.

## 주요 기능

- 🚀 **메모리 효율성**: Apache POI SXSSF 기반의 스트리밍 처리로 일정한 메모리 사용량 유지
- 🧩 **유연한 스키마 정의**:
  - **어노테이션 기반**: `@ExcelSheet`, `@ExcelColumn`으로 DTO에 직접 정의
  - **DSL 기반**: 람다 DSL을 사용하여 런타임에 동적으로 스키마 구성
- 🔌 **다양한 데이터 소스 지원**:
  - **JPA**: `JpaStreamingDataSource` (Stream 기반, 자동 detach 지원)
  - **JDBC**: `JdbcStreamingDataSource` (ResultSet 기반, 커서 유지)
  - **MongoDB**: `MongoStreamingDataSource` (Reactive/Cursor 기반)
- 🍃 **Spring Boot 통합**: `streamsheet-spring-boot-starter`를 통한 자동 구성 (`ExcelExporter` 빈 제공)
- 📊 **진행률 모니터링**: `ExportProgressListener`를 통한 실시간 내보내기 진행 상황 추적
- 🛠 **안전한 리소스 관리**: `StreamingDataSource` 인터페이스를 통한 자동 리소스 정리 (`AutoCloseable`)

## 모듈 구성

| 모듈명 | 설명 |
|---|---|
| `streamsheet-core` | 핵심 로직 (SXSSF, Schema, Exporter Interface) |
| `streamsheet-jdbc` | JDBC `ResultSet` 스트리밍 지원 |
| `streamsheet-jpa` | JPA `Stream` 스트리밍 지원 (Hibernate 등) |
| `streamsheet-mongodb` | MongoDB 데이터 소스 지원 |
| `streamsheet-spring-boot-starter` | Spring Boot 자동 설정 및 편의 기능 |

## 빠른 시작 (Quick Start)

### 1. 의존성 추가

**Spring Boot 사용 시 (권장)**

```kotlin
dependencies {
    // Starter만 추가하면 Core 모듈이 자동으로 포함됩니다.
    implementation("io.github.danpung2:streamsheet-spring-boot-starter:0.0.1-SNAPSHOT")
    
    // 데이터 소스 모듈 (필요 시 선택)
    implementation("io.github.danpung2:streamsheet-jpa:0.0.1-SNAPSHOT")     // JPA 사용 시
    // implementation("io.github.danpung2:streamsheet-jdbc:0.0.1-SNAPSHOT")  // JDBC 사용 시
    // implementation("io.github.danpung2:streamsheet-mongodb:0.0.1-SNAPSHOT") // MongoDB 사용 시
}
```

**일반 Kotlin/Java 프로젝트 (Spring Boot 미사용)**

```kotlin
dependencies {
    implementation("io.github.danpung2:streamsheet-core:0.0.1-SNAPSHOT")

    // 데이터 소스 모듈 (필요 시 선택)
    // implementation("io.github.danpung2:streamsheet-jpa:0.0.1-SNAPSHOT")
    // implementation("io.github.danpung2:streamsheet-jdbc:0.0.1-SNAPSHOT")
    // implementation("io.github.danpung2:streamsheet-mongodb:0.0.1-SNAPSHOT")
}
```

### 2. 스키마 정의 (공통 / Common)

먼저, 엑셀로 변환할 데이터 모델(DTO)을 정의합니다. 이는 Core와 Starter 모두 동일합니다.

```kotlin
@ExcelSheet(name = "주문 목록")
data class OrderExcelDto(
    @ExcelColumn(header = "주문번호", width = 20, order = 1)
    val orderId: String,

    @ExcelColumn(header = "고객명", width = 15, order = 2)
    val customerName: String,

    @ExcelColumn(header = "금액", width = 15, order = 3)
    val amount: Long
)
```

### 3. 사용 방법 (Usage)

환경에 맞는 방법을 선택하세요.

#### Type A: 일반 프로젝트 (Standalone / Core)

`ExcelExporter`를 직접 생성하여 사용합니다.

```kotlin
// 1. 스키마 & 데이터 준비
val schema = AnnotationExcelSchema.create<OrderExcelDto>()
val data = listOf(OrderExcelDto("ORD-001", "홍길동", 15000))

// 2. Exporter 생성 및 실행
val exporter = SxssfExcelExporter()
val dataSource = object : StreamingDataSource<OrderExcelDto> {
    override val sourceName = "ListSource"
    override fun stream(): Sequence<OrderExcelDto> = data.asSequence()
    override fun close() {}
}

FileOutputStream("orders.xlsx").use { output ->
    exporter.export(schema, dataSource, output)
}
```

#### Type B: 스프링 부트 (Spring Boot / Starter)

Starter를 사용하면 `ExcelExporter`가 자동으로 빈(Bean)으로 등록되므로, 주입받아 사용합니다.
또한, `streamsheet.row-access-window-size` 등의 설정을 `application.yml`에서 관리할 수 있습니다.

```kotlin
@Service
class OrderExportService(
    private val excelExporter: ExcelExporter, // 자동 주입 (Auto-wired)
    private val orderRepository: OrderRepository,
    private val entityManager: EntityManager
) {
    @Transactional(readOnly = true)
    fun exportOrders(response: HttpServletResponse) {
        val schema = AnnotationExcelSchema.create<OrderEntity>()
        
        // JPA Streaming DataSource (트랜잭션 필요)
        val dataSource = JpaStreamingDataSource(
            entityManager = entityManager,
            streamProvider = { orderRepository.streamAll() }
        )
        
        response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.setHeader("Content-Disposition", "attachment; filename=orders.xlsx")
        
        // 내보내기 실행 (리소스 자동 해제됨)
        excelExporter.export(schema, dataSource, response.outputStream)
    }
}
```

### MongoDB 데이터 내보내기

```kotlin
@Service
class MongoExportService(
    private val excelExporter: ExcelExporter,
    private val mongoTemplate: MongoTemplate
) {
    fun exportLogs(outputStream: OutputStream) {
         val schema = AnnotationExcelSchema.create<LogDocument>()
         
         // MongoDB Streaming DataSource
         val dataSource = MongoStreamingDataSource.create<LogDocument>(mongoTemplate)
         
         excelExporter.export(schema, dataSource, outputStream)
    }
}
```

## 아키텍처

StreamSheet은 데이터 소스(Source)와 내보내기 엔진(Exporter)을 분리하여 확장성을 높였습니다.

```
┌───────────────────────────────────────┐
│           ExcelExporter               │
│ (SxssfExcelExporter 구현체)            │
│                                       │
│   ┌─────────────┐   ┌─────────────┐   │
│   │ ExcelSchema │   │ DataSource  │   │
│   └──────┬──────┘   └──────┬──────┘   │
│          │                 │          │
└──────────┼─────────────────┼──────────┘
           ▼                 ▼
    ┌─────────────┐   ┌─────────────┐
    │ Schema Info │   │ Data Stream │
    └──────┬──────┘   └──────┬──────┘
           │                 │
           ▼                 ▼
  ┌───────────────────────────────────┐
  │ Apache POI SXSSF Workbook         │
  │ (Windowed Streaming)              │
  └────────────────┬──────────────────┘
                   ▼
            OutputStream (.xlsx)
```

## 설정 옵션 (ExcelExportConfig)

```kotlin
val config = ExcelExportConfig(
    rowAccessWindowSize = 100,  // 메모리에 유지할 행 개수 (기본값: 100)
    flushBatchSize = 1000,      // 디스크로 플러시할 주기 (기본값: 1000)
    compressTempFiles = true    // 임시 파일 압축 여부 (디스크 공간 절약)
)
```

## 오픈소스 고지 (Open Source Notice)

이 프로젝트는 **Apache POI** 라이브러리를 사용합니다.
- **Apache POI**: [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

## 라이선스

MIT License
