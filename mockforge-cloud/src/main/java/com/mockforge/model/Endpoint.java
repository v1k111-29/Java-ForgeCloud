package com.mockforge.model;

// for validation 

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;


import java.time.LocalDateTime;

public class Endpoint {

    // GIVEN VALIDATION ANNOTATIONS, THESE FIELDS WILL BE VALIDATED WHEN SPRING PROCESSES REQUESTS WITH @Valid ANNOTATION IN THE CONTROLLER

    private Long endpointId;

    @NotNull
    @Positive
    private Long workspaceId;
    
    @NotBlank           // Validation annotation to ensure the endpoint name is not blank
    @Size(max = 120) // Validation annotation to ensure the endpoint name does not exceed 120 characters
    private String endpointName;

    @NotBlank
    @Size(max = 255) // Validation annotation to ensure the mock URL path does not exceed 255 characters
    @Pattern(regexp = "/.*", message = "Path must start with /") // Validation annotation to ensure the mock URL path starts with a "/"
    private String mockUrlPath;

    @NotNull
    @Size(max = 32000)
    private String responseBody;

    private LocalDateTime createdAt;

    @NotBlank
    @Pattern // Validation annotation to ensure the HTTP method is one of the allowed values
    (
    regexp = "GET|POST|PUT|PATCH|DELETE",
    message = "HTTP method must be GET, POST, PUT, PATCH, or DELETE"
    )
    private String httpMethod;

    @NotBlank
    @Size(max = 120)
    private String responseContentType; // Added field for Content-Type
    
    private Boolean enabled;



    @NotNull 
    @Min(200) 
    @Max(599)
    private Integer responseStatus; // Added field for HTTP response status

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

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }


    public String getResponseContentType() { return responseContentType; }  
    public void setResponseContentType(String responseContentType) { this.responseContentType = responseContentType; }  

    public Boolean getEnabled() { return enabled; } 
    public void setEnabled(Boolean enabled) { this.enabled = enabled; } 

    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }

}