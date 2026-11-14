# MockForge Cloud

A Java application that lets a developer create fake ("mock") REST APIs, call them over HTTP, and review a log of every call. Built with Spring Boot, plain JDBC and MySQL.

Course project by **Vignesh N** (Roll no: 25011102115).

## What it does

- Create workspaces and, inside them, mock endpoints (method, path, status, content type, body).
- Call a mock at `/mock/workspaces/{workspaceId}/{path}` and get the saved response.
- Optional `PROXY_FALLBACK` mode (GET only): call a real upstream API, fall back to the saved mock body on timeout.
- Every call is logged asynchronously (bounded queue plus worker threads) and can be read through the API.
- Built-in browser tester at `http://localhost:8080`.

## Documents

| File | Content |
|------|---------|
| [MockForge_Project_Report.docx](MockForge_Project_Report.docx) | Full project report: problem statement, design, modules, implementation, 33 manual + 10 automated test cases, screenshots, OO features, future work |

## Project layout

```
mockforge-cloud/
  src/main/java/com/mockforge/
    controller/   REST controllers (workspaces, endpoints, logs, mock execution)
    service/      Business rules, mock and proxy logic, async logger
    repository/   JdbcTemplate SQL (parameterized)
    model/        Workspace, Endpoint, RequestLog, RequestLogEvent
  src/main/resources/static/   Browser tester (HTML/JS/CSS)
  db/schema.sql                Fresh MySQL schema
  db/migrate_existing.sql      Upgrade for an older database
```

Layers: `controller` -> `service` -> `repository` -> MySQL.

## Run

Needs JDK and MySQL (database `mockforge_cloud`, schema from `db/schema.sql`).

```powershell
cd mockforge-cloud
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-27'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run
```

App runs at http://localhost:8080.

Proxy mode needs an exact-origin allowlist set before startup:

```powershell
$env:PROXY_ALLOWED_ORIGINS = 'http://localhost:9001'
```

## Test

```powershell
cd mockforge-cloud
.\mvnw.cmd test
```

Tests use H2 in MySQL mode, so no real database is needed.

## Object-oriented features used

Encapsulation (private fields with getters/setters), immutability (`RequestLogEvent`), abstraction through layers, dependency injection, exception handling with a central `ApiExceptionHandler`, and concurrency (`ArrayBlockingQueue`, worker threads, atomic counters).
