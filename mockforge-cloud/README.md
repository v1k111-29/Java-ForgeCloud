# MockForge Cloud POC

## Start everything with one click

Double-click `start.bat` in the project folder. It opens visible backend/frontend and local upstream API log windows, waits for the MySQL-backed API and opens the customer shop and API explorer. MySQL must already be running with the schema applied and credentials configured. No separate frontend server is needed: Spring Boot serves the pages on port 8080.

Sending mock requests prints REQUEST, UPSTREAM and RESPONSE in the backend console; the log writer prints LOG SAVED after the database insert. The upstream console explains its deliberate delay. Console output is also saved to `logs/backend.log` and `logs/upstream.log`. Keep these windows open while using the app. Use Ctrl+C in each server window to stop it. If a port is already in use the launcher avoids starting another copy and displays the existing component log when available. For changed Java code or changed upstream allowlists, stop the old instance first and launch again.

A workspace contains endpoint definitions. A mock call reads a definition and returns its saved status, content type and body. Logs are saved by two background workers through a bounded queue.

## Start locally

1. Start MySQL. For a new database run `db/schema.sql` in MySQL Workbench.
2. For an existing database inspect its tables first. If it uses the original project columns, run `db/migrate_existing.sql` once instead. Do not run that migration on a fresh schema. If old columns are `endpoint_name` and `mock_url_path`, rename them to `name` and `route_path` before migration and ensure the other schema columns exist. Back up existing data before schema changes. Renaming `mock_endpoints` is explained in the migration.
3. In a VS Code PowerShell terminal set your local credentials and run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-27'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:DB_USERNAME = 'root'
# This local setup currently reads its password from application.properties.
.\mvnw.cmd spring-boot:run
```

Open http://localhost:8080/ for the request tester. Create a workspace, create `/products`, click Load request, then Send request. Click Logs after a short delay. Stop with Ctrl+C. Restarting retains configuration and persisted logs; queued logs are best effort.

By user preference this local setup keeps its database password in application.properties; DB_PASSWORD does not override a literal property value. Optional `DB_URL` overrides the JDBC address. The default uses localhost:3306 and UTC session timestamps. Never commit your real password.

## API

| Method | Path | Meaning |
| --- | --- | --- |
| POST / GET | `/api/workspaces` | Create / list workspaces |
| POST / GET | `/api/endpoints` | Create / list endpoints |
| POST | `/api/workspaces/{id}/endpoints` | Create using the URL workspace and document-compatible name/routePath fields |
| GET | `/api/endpoints/workspace/{id}` | List workspace endpoints |
| GET | `/api/workspaces/{id}/endpoints` | Alias for workspace endpoint listing |
| PATCH | `/api/endpoints/{id}/enabled` | Body: `{"enabled":false}` |
| Any supported method | `/mock/workspaces/{id}/{path}` | Execute a configured route |
| GET | `/api/endpoints/{id}/logs?limit=50` | Endpoint logs; limit clamped to 1..100 |
| GET | `/api/logs` | Latest 50 logs across endpoints |
| GET | `/api/logging/stats` | Queue size, saved, rejected, failed counts |

Create a workspace using `{"name":"Shopping","description":"Demo"}`. Use its returned workspaceId in this endpoint request:

```json
{
  "workspaceId": 1,
  "endpointName": "Products",
  "mockUrlPath": "/products",
  "httpMethod": "GET",
  "responseStatus": 200,
  "responseContentType": "application/json",
  "responseBody": "[{\"id\":1,\"name\":\"Notebook\"}]"
}
```

Call `/mock/workspaces/1/products`. Paths are case-sensitive; one trailing slash is removed for lookup. Query strings do not participate in matching. Unknown, disabled, or wrong-method routes return 404 and have no endpoint-linked log. Validation returns 400, missing workspace/endpoint returns 404, duplicate workspace+method+path returns 409. New endpoints default to enabled and MOCK mode.

## Proxy with timeout fallback

Set an exact origin allowlist BEFORE starting the app:

```powershell
$env:PROXY_ALLOWED_ORIGINS = 'http://localhost:9001'
```

Run `python tools/demo_upstream.py` in another terminal. Create a GET endpoint with the extra fields:

```json
{
  "executionMode": "PROXY_FALLBACK",
  "upstreamUrl": "http://localhost:9001/slow",
  "timeoutMs": 1500
}
```

Include all the normal endpoint fields as well. `/slow` takes three seconds, so the mock body is returned after the 1500 ms deadline. `/products` responds promptly; `/error` returns 500 and passes through. Redirects are returned without following them. Incoming client headers, credentials, bodies, and query strings are not forwarded; the configured upstream URL is used exactly. Only GET supports proxy mode. Connection failures and oversized upstream responses return 502, not fallback. Both mock and upstream bodies are limited to 32 KiB; headers are omitted; only approved integer productId and quantity JSON fields can appear in bounded body samples. Logs record MOCK, UPSTREAM, FALLBACK, or UPSTREAM_ERROR and TIMEOUT when applicable.

## Read the code in this order

1. `model`: plain objects and input constraints.
2. `controller`: HTTP URLs, request validation and response status.
3. `service/WorkspaceService` and `EndpointService`: existence checks and business rules.
4. `repository`: parameterized SQL and reusable row mappers.
5. `service/MockService`: match, serve, optionally call upstream, create a log.
6. `service/AsyncRequestLogger`: finite queue, workers, rejection/failure counters, bounded shutdown.
7. `controller/ApiExceptionHandler`: predictable JSON errors without stack traces in responses.

Controllers receive requests; services decide what should happen; repositories read/write data. The browser dashboard only calls these APIs and renders returned values as text.

## Verify

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Tests use H2 in MySQL mode and a local HTTP test server. They do not touch your MySQL database. They cover creation, validation, duplicate routes, mock responses, missing/wrong routes, concurrent requests, asynchronous log visibility, disabling, timeout fallback, upstream errors, queue rejection, immutable events, body sampling and writer failure counts. The separate real-MySQL verification also passed; see ALIGNMENT.md and logs/mysql-verification-report.json for shutdown, restart persistence, outage and recovery results.

The POC has no authentication and is intended for trusted local use. Queued logs can be lost on a crash; worker failures are counted without retries. Shutdown allows five seconds for workers before interrupting. Delete/update APIs beyond enable toggling are deferred.



## Postman-style endpoint tester

Open http://localhost:8080/. Choose an HTTP method, enter a local /api or /mock path, edit headers as a JSON object and enter an optional request body. Send request displays the actual HTTP status, duration, response size, response headers and formatted response body. HTTP error responses remain visible for inspection. Requests are limited to this MockForge origin; it is not a general-purpose external proxy. Browser-controlled headers such as Host cannot be overridden.

Create/select a workspace and define endpoint responses. Use Load request beside an endpoint to populate the tester, then click Send request. POST examples help create workspaces and endpoint definitions through the same request panel. Request history is limited to ten entries in this tab and is not persisted. Endpoint request logs are saved asynchronously to MySQL; use Logs beside an endpoint to inspect them.

Supported modes are MOCK and PROXY_FALLBACK. The latter uses configured mock data only on timeout, not a previously fetched response. Response caching, recently viewed products and server-off controls were removed. A separate customer shop is available as described below. Existing MySQL records and historical logs were preserved. A definition using an old unsupported execution mode must be recreated in a supported mode and receives HTTP 410 if executed.

Tester files: static/index.html, static/tester.css, static/tester.js. No separate frontend server is required. start.bat opens the shop and API explorer, with the free-form tester linked from the explorer, and starts a local upstream API for timeout-fallback demonstrations.


## Two separate frontends

- Customer shop: http://localhost:8080/shop.html — customer-facing product collection, search, categories and a demo bag with quantity controls. There are no API settings, response-source badges or log panels in the storefront. No checkout, payment or order is processed. Products come from the seeded Shopping Demo workspace and /products mock route. The bag exists in this page's memory only; there is no product response cache.
- API explorer: http://localhost:8080/docs.html — FastAPI-style expandable operation cards with Try it out, path/query parameters, editable JSON, Execute, actual HTTP responses and headers, and recent logs. This is a curated reference frontend for Spring Boot, not an auto-generated OpenAPI schema or a Python FastAPI backend.
- The original Postman-style request tester remains at http://localhost:8080/ for arbitrary local /api and /mock requests.

Start.bat opens both primary frontends after the backend is ready. In the API explorer's Shopping demo controls choose a workspace/source, click Load runtime request below and Execute. Open customer shop uses the selected route via query parameters, for example /shop.html?workspaceId=9&route=/products/slow. Technical configuration stays on the explorer while the customer page remains a normal shop. Run tools/seed_demo.ps1 to prepare the catalog first. Java and the database schema were unchanged for this frontend split.

New assets: static/store.css, static/store.js, static/docs.html, static/explorer.css and static/explorer.js. Replaced the previous technical shop.html with the customer storefront. Product artwork is fixed local SVG illustration; product names and prices are API data. The fonts have local system fallbacks if the optional font service is unavailable.


## Document alignment modules

The document-compatible POST /api/workspaces/{workspaceId}/endpoints now accepts a body using name and routePath; workspaceId comes from the URL. The existing POST /api/endpoints and endpointName/mockUrlPath properties remain compatible. Returned definitions contain both sets of names. If a body workspaceId is provided on the nested route, it must match the URL. The API explorer includes both create operations.

RequestBodySampler reads at most 32 KiB plus one overflow-detection byte. Oversized mock request bodies receive HTTP 413, including requests without Content-Length. Only application/json or application/*+json root objects can produce a sample. The approved allowlist is top-level productId and quantity, limited to whole integers representable as Java long. Strings, nested objects, arrays, malformed JSON, unknown fields and non-JSON bodies are omitted. Samples are at most 256 UTF-8 bytes. Authorization, Cookie and all other headers are never sampled. This is data minimization by allowlist; arbitrary text redaction is not attempted.

RequestLogEvent is an immutable snapshot placed on the bounded queue. The worker converts it to its own RequestLog database model. Changes made to the caller's object after submit cannot change the queued event. The tests demonstrate that behavior.

By explicit user preference, the current local database password remains in application.properties. This is a documented exception to the project document's external-credential recommendation. Do not represent this configuration as credential-free source or use it as a production setup.

The isolated real-MySQL verification tool is tools/verification/verify_mysql.py. It launches a temporary app on a free loopback port and connects only that app through a private MySQL relay. It does not stop MySQL or the existing demo. It creates clearly named Verification workspace records retained for inspection. It tests samples, concurrency, queued graceful shutdown, restart persistence, DB disconnection, safe errors, failure counters and recovery. The temporary app enables Spring's local admin MBean for graceful shutdown; the ordinary app does not. Reports and server logs are written under logs/. Build with mvnw.cmd package before running the Python verification script with JDK 27 available.
