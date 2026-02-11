# StreamSheet

> Memory-efficient Streaming Excel Export SDK for Large-Scale Data

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

## Overview

StreamSheet is a high-performance Excel export library based on **Apache POI SXSSF**.
It handles large datasets (hundreds of thousands of records) stably without OOM (OutOfMemory) errors and supports integration with various data sources (JPA, JDBC, MongoDB, etc.).
You can define Excel schemas intuitively and easily using Kotlin DSL and Annotations.

## Key Features

- 🚀 **Memory Efficiency**: Maintains constant memory usage through Apache POI SXSSF-based streaming.
- 🧩 **Flexible Schema Definition**:
  - **Annotation-Based**: Define directly on DTOs using `@ExcelSheet`, `@ExcelColumn`.
  - **DSL-Based**: Configure schemas dynamically at runtime using Lambda DSL.
- 🔌 **Diverse Data Source Support**:
  - **JPA**: `JpaStreamingDataSource` (Stream-based, supports automatic detach).
  - **JDBC**: `JdbcStreamingDataSource` (ResultSet-based, maintains cursor).
  - **MongoDB**: `MongoStreamingDataSource` (Reactive/Cursor-based).
- 🍃 **Spring Boot Integration**: Automatic configuration via `streamsheet-spring-boot-starter` (provides `ExcelExporter` bean).
- 📊 **Progress Monitoring**: Track export progress in real-time using `ExportProgressListener`.
- 🛠 **Safe Resource Management**: Automatic resource cleanup via `StreamingDataSource` interface (`AutoCloseable`).

## Modules

| Module Name | Description |
|---|---|
| `streamsheet-core` | Core logic (SXSSF, Schema, Exporter Interface) |
| `streamsheet-jdbc` | JDBC `ResultSet` streaming support |
| `streamsheet-jpa` | JPA `Stream` streaming support (Hibernate, etc.) |
| `streamsheet-mongodb` | MongoDB data source support |
| `streamsheet-spring-boot-starter` | Spring Boot auto-configuration and conveniences |

## Demo Project

For an end-to-end, runnable example, see: [StreamSheetDemo-PG](https://github.com/danpung2/StreamSheetDemo-PG)

## Quick Start

### 1. Add Dependencies

**For Spring Boot (Recommended)**

```kotlin
dependencies {
    // Adding the Starter automatically includes the Core module.
    implementation("io.github.danpung2:streamsheet-spring-boot-starter:1.0.0")
    
    // Data Source Modules (Optional)
    implementation("io.github.danpung2:streamsheet-jpa:1.0.0")     // For JPA
    // implementation("io.github.danpung2:streamsheet-jdbc:1.0.0")  // For JDBC
    // implementation("io.github.danpung2:streamsheet-mongodb:1.0.0") // For MongoDB
}
```

**For Standard Kotlin/Java Projects (Non-Spring Boot)**

```kotlin
dependencies {
    implementation("io.github.danpung2:streamsheet-core:1.0.0")

    // Data Source Modules (Optional)
    // implementation("io.github.danpung2:streamsheet-jpa:1.0.0")
    // implementation("io.github.danpung2:streamsheet-jdbc:1.0.0")
    // implementation("io.github.danpung2:streamsheet-mongodb:1.0.0")
}
```

### 2. Define DTO (Common)

First, define the data model (DTO) to be exported. This is common for both Core and Starter.

```kotlin
@ExcelSheet(name = "Order List")
data class OrderExcelDto(
    @ExcelColumn(header = "Order ID", width = 20, order = 1)
    val orderId: String,

    @ExcelColumn(header = "Customer Name", width = 15, order = 2)
    val customerName: String,

    @ExcelColumn(header = "Amount", width = 15, order = 3)
    val amount: Long
)
```

### 3. Execution

Choose the method that fits your environment.

#### Type A: Standalone (Core)

Manually instantiate `ExcelExporter`.

```kotlin
// 1. Prepare Schema & Data
val schema = AnnotationExcelSchema.create<OrderExcelDto>()
val data = listOf(OrderExcelDto("ORD-001", "John Doe", 15000))

// 2. Create Exporter & Execute
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

#### Type B: Spring Boot (Starter)

With the Starter, `ExcelExporter` is automatically registered as a bean, so you can inject it.
You can also manage settings like `streamsheet.row-access-window-size` in `application.yml`.

```kotlin
@Service
class OrderExportService(
    private val excelExporter: ExcelExporter, // Auto-wired
    private val orderRepository: OrderRepository,
    private val entityManager: EntityManager
) {
    @Transactional(readOnly = true)
    fun exportOrders(response: HttpServletResponse) {
        val schema = AnnotationExcelSchema.create<OrderEntity>()
        
        // JPA Streaming DataSource (Requires Transaction)
        val dataSource = JpaStreamingDataSource(
            entityManager = entityManager,
            streamProvider = { orderRepository.streamAll() }
        )
        
        response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.setHeader("Content-Disposition", "attachment; filename=orders.xlsx")
        
        // Execute Export (Resource automatically closed)
        excelExporter.export(schema, dataSource, response.outputStream)
    }
}
```

### Exporting MongoDB Data

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

## Architecture

StreamSheet decouples the Data Source from the Export Engine (Exporter) to enhance extensibility.

```
┌───────────────────────────────────────┐
│           ExcelExporter               │
│ (SxssfExcelExporter Implementation)   │
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

## Configuration Options (ExcelExportConfig)

```kotlin
val config = ExcelExportConfig(
    rowAccessWindowSize = 100,  // Number of rows to keep in memory (Default: 100)
    flushBatchSize = 1000,      // Flush to disk frequency (Default: 1000)
    compressTempFiles = true    // Whether to compress temp files (Saves disk space)
)
```

## Open Source Notice

This project uses the **Apache POI** library.
- **Apache POI**: [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

## License

Apache License 2.0. See `LICENSE` and `NOTICE`.
