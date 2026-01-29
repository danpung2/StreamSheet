# StreamSheet Demo: MongoDB Setup Guide

This demo project demonstrates real-time streaming Excel export functionality using actual MongoDB.

## 1. Running MongoDB
You can easily start MongoDB locally using Docker.

```bash
docker run -d --name streamsheet-mongo -p 27017:27017 mongo:latest
```

## 2. Application Configuration
Ensure that MongoDB connection information is included in the `src/main/resources/application.yml` file.

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/streamsheet_demo
```

## 3. Test Scenarios

### Step 1: Data Seeding
First, fill MongoDB with a large amount of test data.
```bash
curl -X POST "http://localhost:8080/api/demo/mongodb/seed?count=10000"
```

### Step 2: Optimized Async Export (JSON Response)
Request optimized Excel generation (with Projection) asynchronously.
```bash
curl -X POST "http://localhost:8080/api/demo/mongodb/export/optimized"
```
**Response:** Returns `jobId` and `statusUrl`.
```json
{
  "jobId": "...",
  "statusUrl": "http://localhost:8080/api/demo/status/..."
}
```

### Step 3: Check Status & Download (GET)
Check the progress of the async job. When `COMPLETED`, use the `downloadUrl` inside the response to get the file.
```bash
curl -X GET "http://localhost:8080/api/demo/status/{jobId}"
```

### Step 4: Direct Sync Export (Direct Download)
Download the file immediately in a single request (Best for browsers).
```bash
# In browser or via curl (GET)
curl -o result.xlsx http://localhost:8080/api/demo/mongodb/export/download
```

### Step 5: Basic Async Export
Request raw Excel generation including all fields.
```bash
curl -X POST "http://localhost:8080/api/demo/mongodb/export"
```
