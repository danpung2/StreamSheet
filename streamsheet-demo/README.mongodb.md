# StreamSheet Demo: MongoDB Setup Guide

이 데모 프로젝트는 실제 MongoDB를 사용하여 실시간 스트리밍 엑셀 내보내기 기능을 시연합니다.

## 1. MongoDB 실행
Docker를 사용하여 로컬에서 간편하게 MongoDB를 구동할 수 있습니다.

```bash
docker run -d --name streamsheet-mongo -p 27017:27017 mongo:latest
```

## 2. 애플리케이션 설정
`src/main/resources/application.yml` 파일에 MongoDB 연결 정보가 포함되어 있는지 확인합니다.

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/streamsheet_demo
```

## 3. 테스트 시나리오

### 단계 1: 데이터 시딩 (Seed)
먼저 테스트를 위해 몽고디비에 대량의 데이터를 채웁니다.
```bash
curl -X POST "http://localhost:8080/api/demo/mongodb/seed?count=10000"
```

### 단계 2: 일반 내보내기 (Basic Export)
전체 필드를 포함하여 엑셀을 생성합니다.
```bash
curl -X POST "http://localhost:8080/api/demo/mongodb/export"
```

### 단계 3: 고도화된 내보내기 (Optimized Export)
특정 조건(`activityType=SEARCH`)으로 필터링하고, 정렬 및 **Projection(필 필요한 필드만 조회)**이 적용된 최적화된 엑셀을 생성합니다.
```bash
curl -X POST "http://localhost:8080/api/demo/mongodb/export/optimized"
```
