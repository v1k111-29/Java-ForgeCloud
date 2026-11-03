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

@Service
public class MockService {

    private final Logger logger = LoggerFactory.getLogger(MockService.class);

    private final EndpointRepository endpointRepository;

    private final EndpointService endpointService;

    private final AsyncRequestLogger asyncRequestLogger;

    // One HTTP client is shared by the requests that need a real upstream API.
    private final HttpClient upstreamHttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

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

        long requestStartNanoseconds = System.nanoTime();

        LocalDateTime requestReceivedAt = LocalDateTime.now(ZoneOffset.UTC);

        String normalizedRoutePath = EndpointService.normalizePath(mockRoutePath);

        Endpoint endpoint = endpointRepository.findMatchingEndpoint(
            workspaceId, httpMethod, normalizedRoutePath);

        logger.info("REQUEST: workspace={}, method={}, path={}",
            workspaceId, httpMethod, mockRoutePath);

        if (endpoint == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mock endpoint not found");
        }

        // Start with the saved mock response. A successful upstream call can replace it.
        int responseStatus = endpoint.getResponseStatus();

        String responseContentType = endpoint.getResponseContentType();

        byte[] responseBody = endpoint.getResponseBody().getBytes(StandardCharsets.UTF_8);

        String responseSource = "MOCK";

        String fallbackReason = null;

        if (!"MOCK".equals(endpoint.getExecutionMode())
                && !"PROXY_FALLBACK".equals(endpoint.getExecutionMode())) {

            throw new ResponseStatusException(HttpStatus.GONE,
                "Unsupported execution mode. Recreate this endpoint using MOCK or PROXY_FALLBACK.");
        }

        if ("PROXY_FALLBACK".equals(endpoint.getExecutionMode())) {
            URI upstreamUri = endpointService.validateUpstreamUrl(endpoint.getUpstreamUrl());

            logger.info("UPSTREAM: contacting {}://{} with a {} ms deadline",
                upstreamUri.getScheme(), upstreamUri.getRawAuthority(), endpoint.getTimeoutMs());

            HttpRequest upstreamRequest = HttpRequest.newBuilder(upstreamUri)
                .timeout(Duration.ofMillis(endpoint.getTimeoutMs()))
                .GET()
                .build();

            CompletableFuture<HttpResponse<byte[]>> pendingUpstreamResponse =
            upstreamHttpClient.sendAsync(upstreamRequest,
                responseInformation -> new LimitedBodySubscriber());

            try {
                // The deadline includes downloading the body, not just receiving headers.
                HttpResponse<byte[]> upstreamResponse = pendingUpstreamResponse.get(
                    endpoint.getTimeoutMs(), TimeUnit.MILLISECONDS);

                responseStatus = upstreamResponse.statusCode();

                responseContentType = upstreamResponse.headers()
                    .firstValue("Content-Type")
                    .orElse("application/octet-stream");

                responseBody = upstreamResponse.body();

                responseSource = "UPSTREAM";
            } catch (TimeoutException exception) {
                pendingUpstreamResponse.cancel(true);

                responseSource = "FALLBACK";

                fallbackReason = "TIMEOUT";
            } catch (ExecutionException exception) {
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
                pendingUpstreamResponse.cancel(true);

                Thread.currentThread().interrupt();

                throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Request interrupted");
            }
        }

        RequestLog requestLog = new RequestLog();

        requestLog.setEndpointId(endpoint.getEndpointId());

        requestLog.setReceivedAt(requestReceivedAt);

        requestLog.setHttpMethod(httpMethod);

        requestLog.setRequestPath(mockRoutePath);

        requestLog.setResponseStatus(responseStatus);

        long requestDurationMilliseconds =
        (System.nanoTime() - requestStartNanoseconds) / 1_000_000;

        requestLog.setDurationMs((int) Math.min(Integer.MAX_VALUE, requestDurationMilliseconds));

        // The sampler has already removed everything outside the approved field list.
        requestLog.setRequestBodySample(requestBodySample);

        requestLog.setResponseSource(responseSource);

        requestLog.setFallbackReason(fallbackReason);

        logger.info("RESPONSE: source={}, status={}, duration={} ms, reason={}",
            responseSource, responseStatus, requestLog.getDurationMs(),
            fallbackReason == null ? "none" : fallbackReason);

        asyncRequestLogger.submitRequestLog(requestLog);

        // These HTTP responses must not contain a body, even if one was configured.
        if (responseStatus == 204 || responseStatus == 304 || "HEAD".equals(httpMethod)) {
            responseBody = new byte[0];
        }

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
