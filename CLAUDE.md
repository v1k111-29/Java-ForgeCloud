# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Layout

Repo root holds one Maven project: `mockforge-cloud/` (Spring Boot 4.1.2, Java 26, plain JDBC, no JPA). Run all commands from inside `mockforge-cloud/`. See its `README.md` and `STUDY_GUIDE.md` for full API tables and walkthroughs.

## Commands (PowerShell, from `mockforge-cloud/`)

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-27'; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run          # app on http://localhost:8080 (needs MySQL)
.\mvnw.cmd test                     # H2 in MySQL mode, no real DB needed
.\mvnw.cmd test -Dtest=PocFlowTests#methodName   # single test
.\mvnw.cmd package
```

- `start.bat` launches backend + demo upstream (`tools/demo_upstream.py`, port 9001) + opens tester; logs to `logs/`.
- DB: fresh schema = `db/schema.sql`; legacy DB = `db/migrate_existing.sql` (never both). `DB_URL` env overrides JDBC URL.
- `application.properties` hardcodes `spring.datasource.username/password=root`; README says `DB_USERNAME`/`DB_PASSWORD` env vars, but properties do not read them. Check before relying on env vars.
- `PROXY_ALLOWED_ORIGINS` env (exact-origin allowlist) must be set before startup for proxy mode; changing it needs restart.

## Architecture

Layers: `controller` -> `service` -> `repository` (JdbcTemplate, parameterized SQL, row mappers) with plain `model` objects. `ApiExceptionHandler` maps errors to JSON (400 validation, 404 missing, 409 duplicate workspace+method+path, 410 unsupported legacy execution mode, 502 upstream failure); stack traces and messages are suppressed in `application.properties`.

Request flow for mock calls (`/mock/workspaces/{id}/{path}`, handled by `MockExecutionController` -> `service/MockService`):
1. Look up enabled endpoint by workspace + method + path (case-sensitive, one trailing slash stripped, query string ignored). Miss/disabled/wrong method = 404 with no endpoint-linked log.
2. Execution mode `MOCK` returns the saved status/content-type/body. `PROXY_FALLBACK` (GET only) calls the configured `upstreamUrl` via JDK HTTP client with `timeoutMs`; on timeout returns the mock body. Upstream URL must match the allowlist; redirects not followed; client headers/body/query not forwarded; mock and upstream bodies capped at 32 KiB (`LimitedBodySubscriber`); connection failure or oversize = 502, not fallback.
3. Result is a `RequestLog` handed to `AsyncRequestLogger`: bounded queue (`mockforge.logging.capacity`) drained by `mockforge.logging.workers` threads. Full queue rejects (counted), writer exceptions are counted without retry, shutdown waits 5s. Stats at `/api/logging/stats`. Logs are therefore eventually visible and lossy on crash by design. Request bodies/headers are deliberately never persisted.

Frontend is static (`src/main/resources/static/index.html`, `tester.js`, `tester.css`) served by Spring Boot, a Postman-style tester restricted to same-origin `/api` and `/mock` calls. No separate frontend server or build step.

## Tests

`PocFlowTests` (end-to-end flow with local HTTP test server for upstream) and `AsyncRequestLoggerTests`. Both use H2; schema/migration and restart persistence against real MySQL are not covered.
