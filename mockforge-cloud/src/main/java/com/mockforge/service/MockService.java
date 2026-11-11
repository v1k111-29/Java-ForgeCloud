package com.mockforge.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mockforge.model.Endpoint;
import com.mockforge.model.RequestLog;
import com.mockforge.repository.EndpointRepository;

// @Service tells Spring "make ONE object of this class and manage it for me".
// Other classes (like MockExecutionController) then get this object injected
// through their constructor, so we never write "new MockService()" ourselves.
// Job of this class: answer a call to /mock/workspaces/{id}/{path} using the
// endpoint the user saved earlier (or a real upstream API in PROXY_FALLBACK mode).
@Service
public class MockService {

    // Logger writes messages to the console / log file (logger.info(...)).
    private final Logger logger = LoggerFactory.getLogger(MockService.class);

    private final EndpointRepository endpointRepository;

    private final EndpointService endpointService;

    private final AsyncRequestLogger asyncRequestLogger;

    // One HTTP client is shared by the requests that need a real upstream API.
    // connectTimeout(3s): give up if we cannot even open a connection within 3 seconds.
    // Redirect.NEVER: if upstream answers "301 go elsewhere", we do NOT follow it
    // (safety: the allowlist check only approved the original URL).
    private final HttpClient upstreamHttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    // Constructor injection: Spring sees these parameters and passes in the
    // objects it already created. "final" fields mean they never change later.
    public MockService(EndpointRepository endpointRepository,
        EndpointService endpointService,
        AsyncRequestLogger asyncRequestLogger) {

        this.endpointRepository = endpointRepository;

        this.endpointService = endpointService;

        this.asyncRequestLogger = asyncRequestLogger;
    }


    // Find the saved endpoint, prepare its response and send its log to the worker.
    public ResponseEntity<byte[]> executeMockRequest(long workspaceId,
        String httpMethod,
        String mockRoutePath,
        String requestBodySample) {

        // Step 1: start a stopwatch. nanoTime() is only good for measuring
        // durations (not the clock time), which is exactly what we want here.
        long requestStartNanoseconds = System.nanoTime();

        // Real date/time the request arrived, in UTC so it is the same everywhere.
        LocalDateTime requestReceivedAt = LocalDateTime.now(ZoneOffset.UTC);

        // Step 2: clean the path, e.g. "/products/" becomes "/products".
        String normalizedRoutePath = EndpointService.normalizePath(mockRoutePath);

        // Step 3: ask the database for an ENABLED endpoint with this
        // workspace + method + path. Returns null if nothing matches.
        Endpoint endpoint = endpointRepository.findMatchingEndpoint(
            workspaceId, httpMethod, normalizedRoutePath);

        logger.info("REQUEST: workspace={}, method={}, path={}",
            workspaceId, httpMethod, mockRoutePath);

        // No match (wrong path, wrong method, or disabled) -> 404.
        // Throwing here means no log is saved for this request.
        if (endpoint == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mock endpoint not found");
        }

        // Start with the saved mock response. A successful upstream call can replace it.
        int responseStatus = endpoint.getResponseStatus();

        String responseContentType = endpoint.getResponseContentType();

        byte[] responseBody = endpoint.getResponseBody().getBytes(StandardCharsets.UTF_8);

        String responseSource = "MOCK";

        // responseSource ends up as MOCK, UPSTREAM, FALLBACK or UPSTREAM_ERROR.
        // fallbackReason stays null unless something went wrong upstream.
        String fallbackReason = null;

        // Old rows in the database may hold a mode we no longer support.
        // 410 GONE = "this used to exist but is not available anymore".
        // Tip: writing "MOCK".equals(x) instead of x.equals("MOCK") avoids a
        // NullPointerException when x is null.
        if (!"MOCK".equals(endpoint.getExecutionMode())
                && !"PROXY_FALLBACK".equals(endpoint.getExecutionMode())) {

            throw new ResponseStatusException(HttpStatus.GONE,
                "Unsupported execution   mode. Recreate this endpoint using MOCK or PROXY_FALLBACK.");
        }

        // PROXY_FALLBACK: try the real API first, use the saved mock only on timeout.
        if ("PROXY_FALLBACK".equals(endpoint.getExecutionMode())) {
            // Re-check the URL against the allowlist every time (settings may have changed).
            URI upstreamUri = endpointService.validateUpstreamUrl(endpoint.getUpstreamUrl());

            logger.info("UPSTREAM: contacting {}://{} with a {} ms deadline",
                upstreamUri.getScheme(), upstreamUri.getRawAuthority(), endpoint.getTimeoutMs());

            // Build a plain GET. Note: the caller's headers, body and query string
            // are NOT copied into this request.
            HttpRequest upstreamRequest = HttpRequest.newBuilder(upstreamUri)
                .timeout(Duration.ofMillis(endpoint.getTimeoutMs()))
                .GET()
                .build();

            // sendAsync starts the call and returns at once with a CompletableFuture,
            // which is like a "receipt": the answer will be placed in it later.
            // LimitedBodySubscriber reads the body but stops if it grows past 32 KiB.
            CompletableFuture<HttpResponse<byte[]>> pendingUpstreamResponse =
            upstreamHttpClient.sendAsync(upstreamRequest,
                responseInformation -> new LimitedBodySubscriber());

            try {
                // The deadline includes downloading the body, not just receiving headers.
                // get(timeout) waits for the receipt to be filled, but at most timeoutMs.
                HttpResponse<byte[]> upstreamResponse = pendingUpstreamResponse.get(
                    endpoint.getTimeoutMs(), TimeUnit.MILLISECONDS);

                responseStatus = upstreamResponse.statusCode();

                responseContentType = upstreamResponse.headers()
                    .firstValue("Content-Type")
                    .orElse("application/octet-stream");

                responseBody = upstreamResponse.body();

                responseSource = "UPSTREAM";
            } catch (TimeoutException exception) {
                // Our own deadline ran out: stop the call and serve the saved mock body.
                pendingUpstreamResponse.cancel(true);

                responseSource = "FALLBACK";

                fallbackReason = "TIMEOUT";
            } catch (ExecutionException exception) {
                // ExecutionException wraps whatever failed inside the async call.
                // getCause() shows the real error. HttpTimeoutException = the
                // HTTP client's own timeout fired, so we treat it like a timeout too.
                if (exception.getCause() instanceof HttpTimeoutException) {
                    responseSource = "FALLBACK";

                    fallbackReason = "TIMEOUT";
                } else {
                    // Connection failures are errors. Only a timeout uses the mock fallback.
                    responseStatus = 502;

                    responseContentType = "application/json";

                    responseBody = "{\"error\":\"Upstream connection failed\"}"
                        .getBytes(StandardCharsets.UTF_8);

                    responseSource = "UPSTREAM_ERROR";

                    fallbackReason = "UNAVAILABLE";
                }
            } catch (InterruptedException exception) {
                // Our thread was asked to stop while waiting. Cancel the call and
                // set the interrupt flag again so code above us can notice it.
                pendingUpstreamResponse.cancel(true);

                Thread.currentThread().interrupt();

                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Request interrupted");
            }
        }

        // Step 4: fill in a log record describing this call (no headers, no full body).
        RequestLog requestLog = new RequestLog();

        requestLog.setEndpointId(endpoint.getEndpointId());

        requestLog.setReceivedAt(requestReceivedAt);

        requestLog.setHttpMethod(httpMethod);

        requestLog.setRequestPath(mockRoutePath);

        requestLog.setResponseStatus(responseStatus);

        long requestDurationMilliseconds =
        (System.nanoTime() - requestStartNanoseconds) / 1_000_000;

        // Divide nanoseconds by 1,000,000 to get milliseconds. Math.min keeps the
        // number small enough to fit in an int before the (int) cast.
        requestLog.setDurationMs((int) Math.min(Integer.MAX_VALUE, requestDurationMilliseconds));

        // The sampler has already removed everything outside the approved field list.
        requestLog.setRequestBodySample(requestBodySample);

        requestLog.setResponseSource(responseSource);

        requestLog.setFallbackReason(fallbackReason);

        logger.info("RESPONSE: source={}, status={}, duration={} ms, reason={}",
            responseSource, responseStatus, requestLog.getDurationMs(),
            fallbackReason == null ? "none" : fallbackReason);

        // Step 5: hand the log to the background queue. We do NOT wait for the
        // database insert, so the caller gets the response faster.
        asyncRequestLogger.submitRequestLog(requestLog);

        // These HTTP responses must not contain a body, even if one was configured.
        if (responseStatus == 204 || responseStatus == 304 || "HEAD".equals(httpMethod)) {
            responseBody = new byte[0];
        }

        // Step 6: build the HTTP response. ResponseEntity = status + headers + body.
        // Cache-Control: no-store tells browsers not to cache it.
        // X-MockForge-* are our own headers so the tester can show where the answer came from.
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(responseStatus)
            .header("Content-Type", responseContentType)
            .header("Cache-Control", "no-store")
            .header("X-MockForge-Source", responseSource)
            .header("X-MockForge-Duration", String.valueOf(requestLog.getDurationMs()));

        if (fallbackReason != null) {
            responseBuilder.header("X-MockForge-Reason", fallbackReason);
        }

        return responseBuilder.body(responseBody);
    }
}
