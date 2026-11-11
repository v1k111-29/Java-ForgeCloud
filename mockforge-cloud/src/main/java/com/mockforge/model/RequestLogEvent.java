package com.mockforge.model;

import java.time.LocalDateTime;

// Immutable snapshot passed from a request thread to the logging queue.
// Immutable = cannot change after creation: the class is "final" (no subclasses)
// and every field is "final" with no setters. That makes it safe to hand from
// one thread to another: no thread can modify it while another is reading it.
public final class RequestLogEvent {

    private final Long endpointId;

    private final LocalDateTime receivedAt;

    private final String httpMethod;

    private final String requestPath;

    private final Integer responseStatus;

    private final Integer durationMs;

    private final String requestBodySample;

    private final String responseSource;

    private final String fallbackReason;

    // Copy every value out of the (changeable) RequestLog at this moment.
    public RequestLogEvent(RequestLog requestLog) {

        this.endpointId = requestLog.getEndpointId();

        this.receivedAt = requestLog.getReceivedAt();

        this.httpMethod = requestLog.getHttpMethod();

        this.requestPath = requestLog.getRequestPath();

        this.responseStatus = requestLog.getResponseStatus();

        this.durationMs = requestLog.getDurationMs();

        this.requestBodySample = requestLog.getRequestBodySample();

        this.responseSource = requestLog.getResponseSource();

        this.fallbackReason = requestLog.getFallbackReason();
    }


    // Give the worker its own database model, never the caller's original object.
    public RequestLog toRequestLog() {

        RequestLog requestLog = new RequestLog();

        requestLog.setEndpointId(endpointId);

        requestLog.setReceivedAt(receivedAt);

        requestLog.setHttpMethod(httpMethod);

        requestLog.setRequestPath(requestPath);

        requestLog.setResponseStatus(responseStatus);

        requestLog.setDurationMs(durationMs);

        requestLog.setRequestBodySample(requestBodySample);

        requestLog.setResponseSource(responseSource);

        requestLog.setFallbackReason(fallbackReason);

        return requestLog;
    }
}
