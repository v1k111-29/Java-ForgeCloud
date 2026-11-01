package com.mockforge.model;

import java.time.LocalDateTime;

public class Endpoint {
    private Long endpointId;
    private Long workspaceId;
    private String endpointName;
    private String mockUrlPath;
    private String responseBody;
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getEndpointId() { return endpointId; }
    public void setEndpointId(Long endpointId) { this.endpointId = endpointId; }

    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }

    public String getEndpointName() { return endpointName; }
    public void setEndpointName(String endpointName) { this.endpointName = endpointName; }

    public String getMockUrlPath() { return mockUrlPath; }
    public void setMockUrlPath(String mockUrlPath) { this.mockUrlPath = mockUrlPath; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}