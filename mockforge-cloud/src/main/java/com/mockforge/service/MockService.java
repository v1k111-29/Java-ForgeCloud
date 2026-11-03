package com.mockforge.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpResponse;
import com.mockforge.model.Endpoint;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import com.mockforge.model.RequestLog;
import com.mockforge.repository.EndpointRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class MockService {

    private final Logger logger = LoggerFactory.getLogger(MockService.class);

    private final EndpointRepository endpointRepository;
    private final EndpointService endpointService;
    private final AsyncRequestLogger requestLogger;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
    .followRedirects(HttpClient.Redirect.NEVER).build();

    public MockService(EndpointRepository endpointRepository, EndpointService endpointService, AsyncRequestLogger requestLogger) {
        this.endpointRepository = endpointRepository;
        this.endpointService = endpointService;
        this.requestLogger = requestLogger;
    }

    // Find the matching endpoint, prepare its response and queue the request log.
    public ResponseEntity<byte[]> execute(long workspaceId, String httpMethod, String path) {
        long startTime = System.nanoTime();
        LocalDateTime receivedAt = LocalDateTime.now(ZoneOffset.UTC);
        Endpoint endpoint = endpointRepository.findMatch(workspaceId, httpMethod, EndpointService.normalizePath(path));
        logger.info("REQUEST: workspace={}, method={}, path={}", workspaceId, httpMethod, path);
        if (endpoint == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mock endpoint not found");
        }
        int status = endpoint.getResponseStatus();
        String contentType = endpoint.getResponseContentType();
        byte[] body = endpoint.getResponseBody().getBytes(StandardCharsets.UTF_8);
        String source = "MOCK";
        String fallbackReason = null;
        if (!"MOCK".equals(endpoint.getExecutionMode()) && !"PROXY_FALLBACK".equals(endpoint.getExecutionMode())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Unsupported execution mode. Recreate this endpoint using MOCK or PROXY_FALLBACK.");
        }
        if ("PROXY_FALLBACK".equals(endpoint.getExecutionMode())) {
            URI uri = endpointService.validateUpstream(endpoint.getUpstreamUrl());
            logger.info("UPSTREAM: contacting {}://{} with a {} ms deadline", uri.getScheme(), uri.getRawAuthority(), endpoint.getTimeoutMs());
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(endpoint.getTimeoutMs())).GET().build();
            // A deadline covers headers AND body; cancel the request on timeout.
            CompletableFuture<HttpResponse<byte[]>> pendingResponse = client.sendAsync(request, info -> new LimitedBodySubscriber());
            try {
                HttpResponse<byte[]> upstreamResponse = pendingResponse.get(endpoint.getTimeoutMs(), TimeUnit.MILLISECONDS);
                status = upstreamResponse.statusCode();
                contentType = upstreamResponse.headers().firstValue("Content-Type").orElse("application/octet-stream");
                body = upstreamResponse.body();
                source = "UPSTREAM";
            } catch (TimeoutException ex) {
                pendingResponse.cancel(true);
                source = "FALLBACK";
                fallbackReason = "TIMEOUT";
            } catch (ExecutionException ex) {
                if (ex.getCause() instanceof HttpTimeoutException) {
                    source = "FALLBACK";
                    fallbackReason = "TIMEOUT";
                } else {
                    status = 502;
                    contentType = "application/json";
                    body = "{\"error\":\"Upstream connection failed\"}".getBytes(StandardCharsets.UTF_8);
                    source = "UPSTREAM_ERROR";
                    fallbackReason = "UNAVAILABLE";
                }
            } catch (InterruptedException ex) {
                pendingResponse.cancel(true);
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Request interrupted");
            }
        }
        RequestLog log = new RequestLog();
        log.setEndpointId(endpoint.getEndpointId());
        log.setReceivedAt(receivedAt);
        log.setHttpMethod(httpMethod);
        log.setRequestPath(path);
        log.setResponseStatus(status);
        log.setDurationMs((int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startTime) / 1_000_000));
        // Body/header samples are deliberately omitted to avoid persisting credentials.
        log.setResponseSource(source);
        log.setFallbackReason(fallbackReason);
        logger.info("RESPONSE: source={}, status={}, duration={} ms, reason={}",
                source, status, log.getDurationMs(), fallbackReason == null ? "none" : fallbackReason);
        requestLogger.submit(log);
        if (status == 204 || status == 304 || "HEAD".equals(httpMethod)) {
            body = new byte[0];
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .header("Content-Type", contentType)
                .header("Cache-Control", "no-store")
                .header("X-MockForge-Source", source)
                .header("X-MockForge-Duration", String.valueOf(log.getDurationMs()));
        if (fallbackReason != null) response.header("X-MockForge-Reason", fallbackReason);
        return response.body(body);
    }
}
