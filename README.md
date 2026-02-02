# StreamSheet

> Memory-efficient Streaming Excel Export SDK for Large-Scale Data

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![License](https://img.shields.io/badge/license-MIT-blue)]()

## Overview

StreamSheet is a high-performance Excel export library based on **Apache POI SXSSF**.
It handle large datasets (hundreds of thousands of records) stably without OOM (OutOfMemory) errors and supports integration with various data sources (JPA, JDBC, MongoDB, etc.).
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

## Quick Start

### 1. Add Dependencies

**For Spring Boot (Recommended)**

```kotlin
dependencies {
    // Adding the Starter automatically includes the Core module.
    implementation("io.github.danpung2:streamsheet-spring-boot-starter:0.0.1-SNAPSHOT")
    
    // Data Source Modules (Optional)
    implementation("io.github.danpung2:streamsheet-jpa:0.0.1-SNAPSHOT")     // For JPA
    // implementation("io.github.danpung2:streamsheet-jdbc:0.0.1-SNAPSHOT")  // For JDBC
    // implementation("io.github.danpung2:streamsheet-mongodb:0.0.1-SNAPSHOT") // For MongoDB
}
```

**For Standard Kotlin/Java Projects (Non-Spring Boot)**

```kotlin
dependencies {
    implementation("io.github.danpung2:streamsheet-core:0.0.1-SNAPSHOT")
}
```

### 2. Basic Usage (Annotation-Based)

**1) Define DTO**

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

**2) Execute Export**

```kotlin
// Prepare Schema & Data
val schema = AnnotationExcelSchema.create<OrderExcelDto>()
val data = listOf(OrderExcelDto("ORD-001", "John Doe", 15000), ...)

// Create Exporter
val exporter = SxssfExcelExporter()

// Wrap Data Source (Simple implementation for List/Sequence)
val dataSource = object : StreamingDataSource<OrderExcelDto> {
    override val sourceName = "SimpleList"
    override fun stream(): Sequence<OrderExcelDto> = data.asSequence()
    override fun close() {} // No resources to close
}

// Generate Excel File
FileOutputStream("orders.xlsx").use { output ->
    exporter.export(schema, dataSource, output)
}
```

## Spring Boot Integration Example

Using `streamsheet-spring-boot-starter` automatically registers `ExcelExporter` as a bean.

### Exporting JPA Data

```kotlin
@Service
class OrderExportService(
    private val excelExporter: ExcelExporter,
    private val orderRepository: OrderRepository, // JPA Repository
    private val entityManager: EntityManager
) {
    @Transactional(readOnly = true) // Transaction required for Stream maintenance
    fun exportOrders(response: HttpServletResponse) {
        val schema = AnnotationExcelSchema.create<OrderEntity>()
        
        // Create JPA Streaming DataSource
        val dataSource = JpaStreamingDataSource(
            entityManager = entityManager,
            streamProvider = { orderRepository.streamAll() } // Repository method returning Stream<T>
        )
        
        // Set HTTP Response
        response.contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        response.setHeader("Content-Disposition", "attachment; filename=orders.xlsx")
        
        // Export (dataSource is closed automatically)
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

MIT License
