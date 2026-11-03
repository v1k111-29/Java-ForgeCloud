package com.mockforge.model;

import java.time.LocalDateTime;

// Holds the details of one completed call before it is saved or returned as JSON.
public class RequestLog {

    private Long logId;

    private Long endpointId;

    private LocalDateTime receivedAt;

    private String httpMethod;

    private String requestPath;

    private Integer responseStatus;

    private Integer durationMs;

    private String requestBodySample;

    private String responseSource;

    private String fallbackReason;

    // Getters and Setters
    public Long getLogId() {

        return logId;
    }


    public void setLogId(Long logId) {

        this.logId = logId;
    }


    public Long getEndpointId() {

        return endpointId;
    }


    public void setEndpointId(Long endpointId) {

        this.endpointId = endpointId;
    }


    public LocalDateTime getReceivedAt() {

        return receivedAt;
    }


    public void setReceivedAt(LocalDateTime receivedAt) {

        this.receivedAt = receivedAt;
    }


    public String getHttpMethod() {

        return httpMethod;
    }


    public void setHttpMethod(String httpMethod) {

        this.httpMethod = httpMethod;
    }


    public String getRequestPath() {

        return requestPath;
    }


    public void setRequestPath(String requestPath) {

        this.requestPath = requestPath;
    }


    public Integer getResponseStatus() {

        return responseStatus;
    }


    public void setResponseStatus(Integer responseStatus) {

        this.responseStatus = responseStatus;
    }


    public Integer getDurationMs() {

        return durationMs;
    }


    public void setDurationMs(Integer durationMs) {

        this.durationMs = durationMs;
    }


    public String getRequestBodySample() {

        return requestBodySample;
    }


    public void setRequestBodySample(String requestBodySample) {

        this.requestBodySample = requestBodySample;
    }


    public String getResponseSource() {

        return responseSource;
    }


    public void setResponseSource(String responseSource) {

        this.responseSource = responseSource;
    }


    public String getFallbackReason() {

        return fallbackReason;
    }


    public void setFallbackReason(String fallbackReason) {

        this.fallbackReason = fallbackReason;
    }
}
