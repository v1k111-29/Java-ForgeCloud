# MockForge Cloud — completed POC alignment

The core POC is implemented and verified. This is a local prototype, with the explicit differences below. The reference is `MockForge_Cloud_Project_Documentation.docx` from Downloads.

## Requirements and results

| Area | Current behavior | Evidence |
| --- | --- | --- |
| Workspaces | Create and list, validated inputs, generated IDs | HTTP integration tests |
| Endpoint definitions | Create, list globally or per workspace, enable/disable | Integration tests and live demo |
| Document API | POST `/api/workspaces/{workspaceId}/endpoints`, using `name` and `routePath` | Integration tests; older route/field names remain compatible |
| Routing | Workspace + exact method + normalized path + enabled flag | Integration tests; Java equality also protects older case-insensitive MySQL tables |
| Mock responses | Configured status, content type and body; unmatched routes return 404 | Integration tests |
| Timeout fallback extension | GET-only upstream calls to approved origins; timeout uses configured mock; upstream HTTP errors pass through | Local HTTP server tests and shopping rehearsal |
| Request samples | Only approved top-level integer `productId` and `quantity` fields from JSON; no headers or other values | Unit tests, HTTP tests and real MySQL inspection |
| Body bounds | Mock input and upstream response limits are 32 KiB; samples are at most 256 UTF-8 bytes | Sampler/HTTP tests and implementation bounds |
| Async logging | Fixed workers, bounded queue, immediate offer, immutable event snapshots, counters | Queue rejection, writer failure and snapshot tests |
| Shutdown/persistence | Queue drained on graceful close; records still present after restart | Isolated real-MySQL verification |
| Database failure | In-flight proxy response completes while its log write fails; failure counted; new writes recover after reconnect | Isolated real-MySQL verification |
| UTC timestamps | Read and write JDBC LocalDateTime directly in the configured UTC database session | Real-MySQL assertions for workspace/endpoint creation and received request times |
| Errors | Validation 400, missing resources 404, duplicates 409, generic database errors with a reference | HTTP tests and isolated outage check |
| Frontends | Customer shop, FastAPI-style explorer and free-form request tester | Live browser rehearsal |

## Deliberate differences and limits

- The table is named `endpoints`, as agreed, rather than the document's `mock_endpoints`.
- By your explicit choice, the database password stays in `application.properties`. External credential storage from the document is therefore an exception, not a completed requirement.
- Body sampling uses the allowlist you approved instead of trying to redact arbitrary text. Numeric values must be whole integers representable by Java `long`; other values are omitted.
- There is no authentication, response cache, checkout/payment, or general endpoint edit/delete API. Create a new versioned route to change a response; enable/disable is supported.
- Logs are best effort. A crash, full queue, failed insert or expired shutdown wait can lose logs. Failed inserts are counted and are not retried. Counters reset when the app restarts.
- The API explorer is a manually maintained Spring Boot frontend, not a generated OpenAPI schema or a Python FastAPI server.
- Timestamp values in JSON represent UTC. Existing historical rows were not rewritten by the JDBC timestamp correction; records written before it may contain shifted request times.

## Verified results — 3 November 2026

`mvnw.cmd -q package` passed: **10 tests, 0 failures, 0 errors**. The build output is in `logs/alignment-final-build.log`.

The separate real-MySQL run **4a30f231** passed. Its full result is in `logs/mysql-verification-report.json`:

- Stored exactly the approved JSON sample.
- Confirmed workspace, endpoint and request-log timestamps use UTC, independently of this computer's local timezone.
- Served and persisted logs for 20 concurrent requests.
- Observed 5 logs waiting in the queue, then drained all expected records during graceful shutdown.
- Restarted the temporary app and verified all 27 expected records from that stage were still present.
- Disconnected only the temporary app's relay; confirmed a writer failure was counted and management requests returned a safe error.
- Reconnected the relay and confirmed new requests and log inserts recovered.
- Graceful shutdown checks completed in about 1.66 seconds and 1.56 seconds in this run. These are observed timings, not performance guarantees.

MySQL itself and the existing demo were not stopped for the outage test. The final verification fixtures are workspace **12**, endpoint **47**, and proxy endpoint **48**. Earlier verification workspaces **10 and 11** are also retained; workspace 11 contains the browser-created POST `/order-preview` endpoint **46**. Existing workspaces, including **7 and 8**, were not deleted.

## Files to study for this change

| File | What to study |
| --- | --- |
| `src/main/java/com/mockforge/model/RequestLogEvent.java` — new | Copies a mutable request log into final fields before it enters the queue. |
| `src/main/java/com/mockforge/service/RequestBodySampler.java` — new | Reads a bounded body, checks JSON and retains only approved integer fields. |
| `src/main/java/com/mockforge/service/AsyncRequestLogger.java` — edited | Queues immutable events, creates a separate database model inside the worker. |
| `src/main/java/com/mockforge/model/Endpoint.java` — edited | Adds compatible `name`/`routePath` getters and setters. |
| `src/main/java/com/mockforge/controller/EndpointController.java` — edited | Adds the nested create URL and checks URL/body workspace IDs agree. |
| `src/main/java/com/mockforge/service/EndpointService.java` — edited | Validates after the URL has supplied the workspace ID. |
| `src/main/java/com/mockforge/controller/ApiExceptionHandler.java` — edited | Returns validation errors from the service as HTTP 400. |
| `src/main/java/com/mockforge/controller/MockExecutionController.java` — edited | Calls the sampler before running the configured endpoint. |
| `src/main/java/com/mockforge/service/MockService.java` — edited | Includes the approved sample in the request log. |
| `src/main/java/com/mockforge/repository/EndpointRepository.java` — edited | Confirms exact method/path equality after the SQL lookup; reads UTC creation times directly. |
| `src/main/java/com/mockforge/repository/WorkspaceRepository.java` — edited | Reads UTC creation times without conversion through the computer's timezone. |
| `src/main/java/com/mockforge/repository/RequestLogRepository.java` — edited | Reads and writes UTC LocalDateTime values directly instead of converting through java.sql.Timestamp. |
| `src/main/resources/static/explorer.js` — edited | Shows the document-compatible endpoint creation operation. |
| `src/main/resources/static/index.html` — edited | Explains which request fields can be logged. |
| `src/test/java/com/mockforge/RequestBodySamplerTests.java` — new | Checks approved fields, omitted values and streaming size limits. |
| `src/test/java/com/mockforge/PocFlowTests.java` — edited | Checks both API contracts and stored samples through HTTP. |
| `src/test/java/com/mockforge/AsyncRequestLoggerTests.java` — edited | Checks that changing a submitted model cannot change its queued snapshot. |
| `tools/verification/mysql_relay.py` — new | Controls only the temporary verification app's database connection. |
| `tools/verification/ShutdownApp.java` — new | Requests graceful shutdown of only the temporary app. |
| `tools/verification/verify_mysql.py` — new | Coordinates concurrency, shutdown, restart, outage and recovery checks. |
| `README.md`, `STUDY_GUIDE.md`, `DEMO.md` — edited | Startup, learning order and presentation steps. |

`application.properties` was left unchanged in this alignment pass. No schema migration is needed for these additions.

## Final live rehearsal

The updated backend was started on port 8080 with the local upstream on 9001. Running the seed script twice reused workspace **9** and all four existing definitions without duplicates. The live requests passed: Mock 200/MOCK, Live 200/UPSTREAM, Slow 200/FALLBACK (about 1,531 ms), Broken 500/UPSTREAM, and wrong-case `/Products` 404. Results are in `logs/live-demo-verification.json`.

The browser explorer created endpoint **46** with the document-compatible POST URL, executed its POST `/order-preview` response with HTTP 201, then fetched its saved log. The sample was exactly `{"productId":42,"quantity":2}` despite extra unapproved fields in the request. The customer shop rendered all five configured products. Backup images are `demo-backups/09-allowlisted-request-log.png` and `demo-backups/10-final-storefront.png`.

## Understand the demo in one minute

The shop is a customer of MockForge. Its JavaScript asks for products. MockForge reads the endpoint configuration from MySQL, then either returns the saved product list or calls the approved upstream API. If that API exceeds its deadline, MockForge returns the configured fallback list. A background worker saves a request log independently of the response.

The API explorer lets you inspect those same HTTP requests. It does not power the shop directly; both pages call the same Java backend.

Follow the presentation in `DEMO.md`, then study one request through controller → service → repository. Study the queue and verification tools last.
