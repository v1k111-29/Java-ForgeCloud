
// Tells the application that this class is a model representing an endpoint in the system, 
// with various properties and validation constraints.

package com.mockforge.model;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
public class Endpoint {

    private Long endpointId;

    @NotNull // This value must be provided.
    @Positive
    private Long workspaceId;

    @NotBlank // Required field; spaces alone are not allowed.
    @Size(max = 120) // Must fit in the database column.
    private String endpointName;

    @NotBlank // Required field; spaces alone are not allowed.
    @Size(max = 255)
    @Pattern(regexp = "/.*")
    private String mockUrlPath;

    @NotBlank // Required field; spaces alone are not allowed.
    @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")
    private String httpMethod;

    @NotNull // This value must be provided.
    @Min(200)
    @Max(599)
    private Integer responseStatus;

    @NotBlank // Required field; spaces alone are not allowed.
    @Size(max = 120) // Must fit in the database column.
    private String responseContentType;

    @NotNull // This value must be provided.
    @Size(max = 32000)
    private String responseBody;
    private Boolean enabled = true;
    private LocalDateTime createdAt;

    @NotNull // This value must be provided.
    @Pattern(regexp = "MOCK|PROXY_FALLBACK")
    private String executionMode = "MOCK";

    @Size(max = 2000)
    private String upstreamUrl;

    @NotNull // This value must be provided.
    @Min(100)
    @Max(10000)
    private Integer timeoutMs = 1500;

    // Getters and Setters
    public Long getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(Long endpointId) {
        this.endpointId = endpointId;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public void setEndpointName(String endpointName) {
        this.endpointName = endpointName;
    }

    public String getMockUrlPath() {
        return mockUrlPath;
    }

    public void setMockUrlPath(String mockUrlPath) {
        this.mockUrlPath = mockUrlPath;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseContentType() {
        return responseContentType;
    }

    public void setResponseContentType(String responseContentType) {
        this.responseContentType = responseContentType;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    public String getUpstreamUrl() {
        return upstreamUrl;
    }

    public void setUpstreamUrl(String upstreamUrl) {
        this.upstreamUrl = upstreamUrl;
    }

    public Integer getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Integer timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
