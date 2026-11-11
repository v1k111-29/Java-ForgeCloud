package com.mockforge.model;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// These settings describe the response MockForge should give for one route.
// Plain Java object (model). Jackson turns it to/from JSON; the repository
// fills it from a database row. The annotations are validation rules checked
// by @Valid / the Validator:
//   @NotNull = must be present, @NotBlank = not null and not just spaces,
//   @Size(max) = length limit, @Min/@Max = number range, @Positive = > 0,
//   @Pattern(regexp) = must match the regex, e.g. "/.*" means "starts with /".
// Wrapper types (Long, Integer, Boolean) are used so "missing" can be null.

public class Endpoint {

    // MySQL generates the ID and creation time when the endpoint is saved.
    private Long endpointId;

    // Every endpoint belongs to an existing workspace.
    @NotNull
    @Positive
    private Long workspaceId;

    @NotBlank
    @Size(max = 120)
    private String endpointName;

    @NotBlank
    @Size(max = 255)
    @Pattern(regexp = "/.*")
    private String mockUrlPath;

    @NotBlank
    @Pattern(regexp = "GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS")
    private String httpMethod;

    @NotNull
    @Min(200)
    @Max(599)
    private Integer responseStatus;

    @NotBlank
    @Size(max = 120)
    private String responseContentType;

    @NotNull
    @Size(max = 32000)
    private String responseBody;

    // A disabled endpoint stays saved, but cannot answer mock requests.
    private Boolean enabled = true;

    private LocalDateTime createdAt;

    // MOCK = always return the saved body. PROXY_FALLBACK = call upstreamUrl,
    // use the saved body only if upstream times out. Default is MOCK.
    @NotNull
    @Pattern(regexp = "MOCK|PROXY_FALLBACK")
    private String executionMode = "MOCK";

    @Size(max = 2000)
    private String upstreamUrl;

    // Upstream deadline in milliseconds: 100 to 10000 (0.1 s to 10 s), default 1500.
    @NotNull
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


    // Document-compatible JSON name; the existing endpointName property still works.
    public String getName() {

        return endpointName;
    }


    public void setName(String name) {

        this.endpointName = name;
    }


    public String getMockUrlPath() {

        return mockUrlPath;
    }


    public void setMockUrlPath(String mockUrlPath) {

        this.mockUrlPath = mockUrlPath;
    }


    // Document-compatible JSON routePath; both names refer to the same value.
    public String getRoutePath() {

        return mockUrlPath;
    }


    public void setRoutePath(String routePath) {

        this.mockUrlPath = routePath;
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
