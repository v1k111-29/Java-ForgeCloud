# MockForge Cloud Demo A + B

An eight-minute demonstration of an API tester, a frontend consuming mock data, and configured timeout fallback. No response cache, shopping checkout, or server-control feature is involved. See ALIGNMENT.md for the latest Java/API additions and verification results.

## Before the presentation

1. Start MySQL with the existing `mockforge_cloud` schema and connection settings.
2. Double-click `start.bat`. Keep the backend and upstream log consoles open.
3. In a VS Code PowerShell terminal at the project root, run:

   ```powershell
   .\tools\seed_demo.ps1
   ```

4. Note the printed workspace ID. The seed script reuses `Shopping Demo` and ignores endpoint creation conflicts (409). It does not reset existing definitions, enable disabled routes, or delete records. Read any warnings before presenting.
5. Open two tabs:
   - API explorer: http://localhost:8080/docs.html
   - Customer shop: http://localhost:8080/shop.html
   - Optional free-form request tester: http://localhost:8080/
6. The shop finds the seeded workspace by name. Its API controls are on the separate explorer page. Choose a source there, click **Load runtime request below**, then **Execute** to inspect it or **Open customer shop** to see the consumer. The shop receives workspace/route through URL parameters.
7. In the API explorer confirm all four sources work. Mock shows five configured products. Live upstream shows four products with `Upstream` in their names. Slow uses the same configured product array as Mock. Broken shows an error without product cards.

The launcher must approve `http://localhost:9001` as an upstream origin; its existing default does. The shop and tester share Spring Boot's origin, so no CORS configuration or frontend build is needed. Python is needed only for the small local upstream service.

## Run order

### 0:00–1:00 — Explain the problem

“A frontend can be ready before its real backend. MockForge lets us define the response in a database, call it through HTTP, and inspect the resulting request log.”

Explain that the shop is a separate consumer page: its JavaScript asks MockForge for products and renders the returned array. The catalog itself lives in the endpoint definition or upstream API, not in the shop HTML.

### 1:00–3:00 — Demo A: define and test an endpoint

1. In the tester, create a small workspace by hand, for example `Presentation A`.
2. Expand **Create a mock endpoint**. Create GET `/hello`, status `200`, content type `application/json`, mode `MOCK`, and response body `{"message":"Hello from MockForge"}`.
3. Click **Load request** beside `/hello`, then **Send request**. Show status, duration, headers, and JSON.
4. Change the request method to POST and send it. Show the 404 caused by a method mismatch.
5. Change back to GET, disable the saved endpoint using **Toggle enabled**, and send again. Show 404. Re-enable it before continuing.
6. Send GET again, then click **Logs**. Explain that successful matched calls are queued and saved asynchronously. An unmatched route has no endpoint-linked log.

### 3:00–4:30 — Demo B: the same API drives a shop

1. Open the customer shop. Show five products, search, and the demo bag. There are no API controls on this page. In the API explorer choose Mock and execute the runtime request to show `MOCK` in its response.
2. In the API explorer point out the actual `/mock/workspaces/ID/products` request URL, status, headers and body.
3. Demonstrate a changed response without changing the frontend:
   - The current API has no endpoint update operation. Editing the request body in the tester alone does not update an existing saved endpoint; POSTing the same method/path returns 409.
   - In the tester, select the `Shopping Demo` workspace and create a NEW MOCK endpoint GET `/products-v2` with this response body:

     ```json
     [
       {"id": 10, "name": "New Demo Keyboard", "price": 39.00},
       {"id": 11, "name": "New Demo Mouse", "price": 18.00}
     ]
     ```

   - In the API explorer choose Custom mock route, enter `/products-v2`, then click **Open customer shop**.
   - Show that the same shop now displays Keyboard and Mouse. No frontend code changed.
   - Restore the source to Mock in the explorer afterward.

If you rehearse repeatedly and `/products-v2` already exists, use a new versioned path. Do not promise an endpoint editor that the backend does not implement.

### 4:30–5:30 — Fast upstream

In the API explorer choose **Live upstream**, click **Load runtime request below** and **Execute**. MockForge calls the local Python API at `/products`, receives its product array, and passes it back. Show HTTP 200, `UPSTREAM`, and the corresponding live log entry.

This is a local demonstration API, not a real retailer. Its distinct product names make the different response visible.

### 5:30–6:30 — Timeout fallback

In the API explorer choose **Slow upstream → fallback**, load the runtime request and execute it. The upstream waits three seconds; MockForge's deadline is 1,500 ms. The caller waits roughly 1.5 seconds plus processing overhead, then receives the configured mock response.

Show HTTP 200 and `FALLBACK`. Within a polling cycle the saved log should show `FALLBACK`, reason `TIMEOUT`, and server duration around 1,500 ms.

“This is a prepared fallback response, not cached data or previously fetched live data.”

### 6:30–7:00 — Error passthrough

In the API explorer choose **Broken upstream → HTTP 500**, load the runtime request and execute it. Show HTTP 500 and the visible error state. Its log source is `UPSTREAM`, not `FALLBACK`.

“The server answered with an error before the deadline. Our policy passes upstream HTTP errors through; only a timeout uses the configured fallback.”

### 7:00–8:00 — Logs, concurrency design, and limits

1. Show the two consoles: REQUEST / UPSTREAM / RESPONSE / LOG SAVED, and the upstream's intentional delay.
2. Open http://localhost:8080/api/logging/stats. Explain queued, saved, rejected, and failed counters.
3. The API explorer polls `/api/logs` every two seconds and shows the latest ten global entries from the latest 50 records. High traffic may push older entries out of that window.
4. Each operation response uses its existing `X-MockForge-Source` header. The live log table is a separate global history, not an exact per-request correlation. There is no unique request correlation ID.
5. Close with the limits: no authentication, trusted local use, best-effort in-memory log queue, possible log loss on crash or overload, no cache or guaranteed log durability. Do not expose the prototype publicly.

## Seeded endpoints

| Mock route | Mode | Upstream | Expected result |
| --- | --- | --- | --- |
| GET `/products` | MOCK | None | 200, five configured products, MOCK |
| GET `/products/live` | PROXY_FALLBACK | `http://localhost:9001/products` | 200, four upstream products, UPSTREAM |
| GET `/products/slow` | PROXY_FALLBACK | `http://localhost:9001/slow` | About 1.5 s, 200, configured products, FALLBACK/TIMEOUT |
| GET `/products/broken` | PROXY_FALLBACK | `http://localhost:9001/error` | 500 error, UPSTREAM |

All full mock URLs start with `/mock/workspaces/YOUR_ID`. All three proxy definitions use a 1,500 ms timeout.

## Verification and backup

Run from the project root:

```powershell
.\mvnw.cmd test
.\tools\seed_demo.ps1
.\tools\seed_demo.ps1
```

The second seed run should reuse the same workspace and report existing endpoint definitions. It must not create duplicates. Automated tests use H2 and do not alter your MySQL data.

Browser verification: use the API explorer to exercise source options and inspect HTTP errors. Check the shop search, bag, quantity changes and a consumer URL opened from the explorer. Missing/failed collection responses produce a customer-friendly retry state. Backup screenshots are saved under `demo-backups/` after live verification. If a live dependency fails, use those screenshots and disclose that they are from rehearsal.

Existing test workspaces, including IDs 7 and 8 if present, are not removed by the seed script. Cleanup is a separate action requiring confirmation.


## Frontend split rehearsal

The current shop is customer-only; all developer controls and logs are in docs.html. In the explorer, each route expands independently. Try it out enables its inputs, and Execute sends a real request. GET /api/workspaces, POST validation (blank name -> 400), and the slow runtime response (200 with FALLBACK/TIMEOUT) were verified after the split. Shop search and bag totals were also checked. No Java changes were needed.


## Document-compatible API and logging demonstration

The API explorer now includes POST /api/workspaces/{workspaceId}/endpoints. Use name and routePath in its JSON body; the older route/fields still work. Create a POST mock such as /order-preview and call it with {"productId":42,"quantity":2,"password":"demo-secret"}. Its requestBodySample should contain only productId and quantity. The password and all headers are omitted. This is a fake response demonstration, not an actual order.

Queue events are immutable snapshots. The external-credential recommendation is intentionally not adopted for this local setup: the user chose to keep the password in application.properties. The final alignment verification report distinguishes that exception from completed requirements.
