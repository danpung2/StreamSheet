# StreamSheet Demo

A comprehensive demo application showcasing StreamSheet's high-performance Excel export capabilities with various data sources.

See also: `../README.md`

## Features

- **Web Dashboard**: Interactive UI to trigger exports and monitor job progress
- **Multiple Data Sources**: MongoDB, PostgreSQL (JDBC/JPA), and in-memory mock data
- **Async Export**: Non-blocking Excel generation with real-time status polling
- **Streaming Architecture**: Constant memory footprint regardless of data size

## Prerequisites

- Java 17+
- Docker & Docker Compose (for databases)

## Quick Start

### 1. Start Infrastructure

```bash
cd streamsheet-demo
docker-compose up -d
```

This starts:
- **MongoDB**: `localhost:27017`
- **PostgreSQL**: `localhost:5432` (user: `user`, password: `password`)

### 2. Run the Application

```bash
# From project root
./gradlew :streamsheet-demo:bootRun
```

### 3. Open Dashboard

Navigate to [http://localhost:8080](http://localhost:8080) in your browser.

## API Endpoints

### Data Seeding

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/demo/mongodb/seed?count=N` | Seed N records into MongoDB |
| POST | `/api/demo/postgres/seed?count=N` | Seed N records into PostgreSQL |

### Export Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/demo/export?count=N` | Mock data export (no DB) |
| POST | `/api/demo/mongodb/export` | MongoDB streaming export |
| POST | `/api/demo/mongodb/export/optimized` | MongoDB with Projection |
| POST | `/api/demo/jdbc/export` | JDBC ResultSet streaming |
| POST | `/api/demo/jpa/export` | JPA Entity streaming |
| GET | `/api/demo/mongodb/export/download` | Sync direct download |

### Job Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/demo/status/{jobId}` | Get job status and download URL |

## Architecture

```
streamsheet-demo/
├── src/main/kotlin/com/streamsheet/demo/
│   ├── api/                    # REST Controllers
│   │   └── DemoController.kt
│   ├── domain/
│   │   ├── model/              # Shared models
│   │   │   └── UserActivity.kt
│   │   ├── mongo/              # MongoDB documents
│   │   │   ├── UserActivityDocument.kt
│   │   │   ├── UserActivityProjection.kt
│   │   │   └── UserActivityRepository.kt
│   │   └── jpa/                # JPA entities
│   │       ├── UserActivityEntity.kt
│   │       └── UserActivityJpaRepository.kt
│   └── service/                # Business logic (future)
├── src/main/resources/
│   ├── static/
│   │   └── index.html          # Web Dashboard
│   └── application.yml         # Configuration
└── docker-compose.yml          # Infrastructure
```

## Export Flow

1. **User Request** → Start export via API or Dashboard
2. **Job Creation** → `AsyncExportService` creates a job and returns `jobId`
3. **Background Processing** → StreamSheet writes Excel in streaming mode
4. **Status Polling** → Frontend polls `/status/{jobId}` for progress
5. **Download** → When `COMPLETED`, `downloadUrl` is available

## Configuration

Key settings in `application.yml`:

```yaml
streamsheet:
  row-access-window-size: 100    # SXSSF memory window
  flush-batch-size: 1000         # Rows per flush
  compress-temp-files: true      # Reduce disk usage
  storage:
    type: LOCAL                  # LOCAL or S3/GCS
    local:
      path: build/exports
      base-url: http://localhost:8080/api/streamsheet/download
  retention-hours: 1             # Auto-cleanup after 1 hour
```

## License

Apache License 2.0. See `../LICENSE` and `../NOTICE`.
