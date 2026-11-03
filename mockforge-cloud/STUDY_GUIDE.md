# MockForge Cloud study guide

## What changed in this refactor

The 17 Java files below were made easier to read. Short variable names were replaced with names such as workspace, endpoint and jdbcTemplate. Methods, getters, setters, conditionals and catch blocks are spaced out. Imports are explicit. Validation error handling uses a loop instead of a stream. EnabledRequest is a small class with getters and setters instead of a record. Comments explain the purpose of methods and the important validation/threading rules.

This refactor preserves the API behavior. Constructor injection is retained: Spring supplies the repositories/services a class needs. The concurrency and HTTP client safeguards are retained because they are required for the feature to work correctly.

The status column below describes the earlier implementation as well as this refactor. No additional runtime Java files were created during this refactor. This study guide is new. MockforgeCloudApplication.java was not changed.

## Study order and Java file inventory

Read one row at a time from the top. Do not start by trying to understand the background worker or body subscriber.

| Order | File | Earlier implementation | Purpose |
| --- | --- | --- | --- |
| 1 | [Workspace.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/model/Workspace.java>) | Edited existing | Holds workspace data and validates its name and description. |
| 2 | [WorkspaceRepository.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/repository/WorkspaceRepository.java>) | Edited existing | Workspace SQL, row mapping, generated ID, and existence check. |
| 3 | [WorkspaceService.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/service/WorkspaceService.java>) | Created in implementation | Workspace checks and creation. |
| 4 | [WorkspaceController.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/controller/WorkspaceController.java>) | Edited existing | HTTP workspace create/list routes. |
| 5 | [Endpoint.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/model/Endpoint.java>) | Edited existing | Endpoint data, validation rules and proxy settings. |
| 6 | [EndpointRepository.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/repository/EndpointRepository.java>) | Edited existing | Save, list, find, match and enable endpoints. |
| 7 | [EndpointService.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/service/EndpointService.java>) | Created in implementation | Validate configuration, check ownership and check approved upstream origins. |
| 8 | [EndpointController.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/controller/EndpointController.java>) | Edited existing | Endpoint create/list/enable routes; includes the small EnabledRequest class. |
| 9 | [MockExecutionController.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/controller/MockExecutionController.java>) | Edited existing | Extract the workspace and mock path from an incoming request. |
| 10 | [MockService.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/service/MockService.java>) | Created in implementation | Find the endpoint, return a mock or upstream response, and prepare its log. |
| 11 | [RequestLog.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/model/RequestLog.java>) | Edited existing | Holds the details of a completed request and the response source. |
| 12 | [RequestLogRepository.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/repository/RequestLogRepository.java>) | Edited existing | Insert request logs and read recent logs. |
| 13 | [LogController.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/controller/LogController.java>) | Created in implementation | Endpoint-specific logs and logging statistics. |
| 14 | [DashboardController.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/controller/DashboardController.java>) | Formatting only in this refactor | Existing route for the latest 50 logs across endpoints. |
| 15 | [ApiExceptionHandler.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/controller/ApiExceptionHandler.java>) | Created in implementation | Convert validation/database/service failures into JSON HTTP errors. |
| 16 | [AsyncRequestLogger.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/service/AsyncRequestLogger.java>) | Created in implementation | Put logs in a finite queue and save them using background workers. |
| 17 | [LimitedBodySubscriber.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/java/com/mockforge/service/LimitedBodySubscriber.java>) | Created in implementation | Stop an upstream download when its body exceeds 32 KiB. |

## Start with one complete request

Follow workspace creation first:

1. The browser sends JSON to POST /api/workspaces.
2. WorkspaceController receives a Workspace object. @Valid checks the annotations on its fields.
3. WorkspaceService.createWorkspace() trims the name and asks the repository to save it.
4. WorkspaceRepository inserts a row using JdbcTemplate. PreparedStatement fills the SQL placeholders. GeneratedKeyHolder reads the new ID.
5. The repository reads the created row and returns a Workspace object.
6. The controller sends that object as JSON with status 201.

After studying that flow, follow endpoint creation in the same order: model, repository, service, controller.

## What to look for in each kind of file

- Model: private fields hold data; getters read them; setters update them. Annotations describe allowed input values.
- Repository: String sql holds a query; question marks receive separate values; RowMapper turns each database row into an object. A repository does not choose HTTP response codes.
- Service: checks such as workspace existence and valid proxy configuration. Controllers call these methods.
- Controller: URL mappings, request parameters, validation activation and response statuses.
- AsyncRequestLogger: submitRequestLog() uses offer(), which returns immediately. saveQueuedLogs() runs on worker threads. If the queue is full, the rejected counter increases.
- MockService: begins with the saved mock response. Proxy mode replaces it only after the upstream response succeeds. Timeout leaves the mock response in place and records FALLBACK.

## Concepts that are new compared with your original code

- A constructor receives dependencies from Spring. final means the field cannot be reassigned after construction; it does not make the entire object immutable.
- A lambda such as (rs, row) -> { ... } supplies mapping instructions to JdbcTemplate.
- this::saveQueuedLogs tells the executor which method to run in the background.
- CompletableFuture represents the pending upstream response. get(timeout, unit) waits only for the configured deadline.
- AtomicLong keeps counters safe when several workers update them. volatile makes the accepting flag visible between threads.
- synchronized coordinates log submission with shutdown. Keep it when studying the worker; removing it can change shutdown behavior.
- LimitedBodySubscriber receives chunks of upstream bytes. Study it last; its purpose is just to enforce the body-size limit.

## Other files created or edited in the implementation

| File | Change | Purpose |
| --- | --- | --- |
| [schema.sql](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/db/schema.sql>) | Edited | Fresh MySQL schema, including proxy/log-source columns. |
| [migrate_existing.sql](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/db/migrate_existing.sql>) | Created | One-time migration for the original existing schema; inspect before running. |
| [application.properties](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/resources/application.properties>) | Edited | Database environment variables, UTC connection and queue/proxy settings. |
| [index.html](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/main/resources/static/index.html>) | Edited | Dashboard for workspaces, endpoints, calls and logs. |
| [pom.xml](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/pom.xml>) | Edited | Added H2 as a test-only database dependency. |
| [PocFlowTests.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/test/java/com/mockforge/PocFlowTests.java>) | Created | HTTP integration tests with a temporary database and upstream server. |
| [AsyncRequestLoggerTests.java](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/src/test/java/com/mockforge/AsyncRequestLoggerTests.java>) | Created | Queue-full and writer-failure tests. |
| [demo_upstream.py](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/tools/demo_upstream.py>) | Created | Optional local API for fast, slow and error demonstrations. |
| [README.md](<C:/Users/salem/OneDrive/Desktop/java/java project/mockforge-cloud/README.md>) | Created | Startup instructions, API examples and limitations. |

Existing MockforgeCloudApplicationTests.java was retained. The Java refactor did not change the database scripts, dashboard, configuration, POM or tests. The earlier temporary generation scripts were removed. Automated tests use H2; see ALIGNMENT.md and logs/mysql-verification-report.json for the subsequent real-MySQL verification status.


## Current endpoint tester

The main page is a local Postman-style request tester. static/index.html defines the forms, static/tester.css styles them, and static/tester.js sends requests and renders actual responses. The customer shop is at /shop.html and the API explorer is at /docs.html. The response cache was removed. Core controller/service/repository responsibilities above are unchanged. Start.bat and tools/start_demo.py open the shop and explorer. Old database definitions are retained but unsupported execution modes must be recreated.


## Alignment modules added afterward

- model/RequestLogEvent.java is an immutable queue snapshot. RequestLog remains a mutable database/JSON model.
- service/RequestBodySampler.java enforces the 32 KiB mock-body limit and samples only approved top-level productId and quantity integers. Header values and all other data are omitted.
- EndpointController also supports POST /api/workspaces/{workspaceId}/endpoints. Endpoint accepts and returns document names name/routePath plus compatible endpointName/mockUrlPath properties.
- tools/verification/mysql_relay.py and verify_mysql.py run an isolated failure/restart check against local MySQL. ShutdownApp.java gracefully closes just that temporary app using its locally enabled admin bean.
- Credential handling remains a deliberate exception: the user asked to keep the password in application.properties.

See ALIGNMENT.md for the complete file list, simple request-flow explanation and verified results. All 10 automated tests passed. The isolated real-MySQL test also passed concurrency, queued shutdown, restart persistence, database disconnect and recovery checks.


## Readability refactor

See REFACTOR_NOTES.md for the complete edited-file list and the current method names. The code uses full variable names, separate statements, extra space between logical steps, and comments explaining decisions. HTTP URLs, JSON fields, schema and behavior are preserved.
