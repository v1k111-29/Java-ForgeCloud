package com.mockforge.controller;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mockforge.service.MockService;
import com.mockforge.service.RequestBodySampler;

// @RestController = this class answers HTTP requests, and whatever a method
// returns is written straight into the HTTP response (not a web page template).
// This controller handles every call to /mock/... (the fake APIs users created).
@RestController
public class MockExecutionController {

    private final MockService mockService;

    private final RequestBodySampler requestBodySampler;

    public MockExecutionController(MockService mockService, RequestBodySampler requestBodySampler) {

        this.mockService = mockService;

        this.requestBodySampler = requestBodySampler;
    }


    // Get the route after the workspace ID and execute the mock request.
    // @RequestMapping with no method = accepts ALL methods (GET, POST, PUT, ...).
    // "/**" = match anything after, e.g. /mock/workspaces/1/products/5.
    // @PathVariable long workspaceId copies the {workspaceId} part of the URL
    // into the parameter ("1" -> 1L). HttpServletRequest = the raw incoming request.
    // Example: URI "/mock/workspaces/1/products/" -> prefix "/mock/workspaces/1"
    // -> mockRoutePath "/products/" (MockService later trims the last slash).
    // getRequestURI() never includes the "?query" part, so the query is ignored.

    
    @RequestMapping("/mock/workspaces/{workspaceId}/**")
    public ResponseEntity<byte[]> executeMockRequest(@PathVariable long workspaceId, HttpServletRequest httpRequest) throws IOException {

        String workspaceUrlPrefix = httpRequest.getContextPath() + "/mock/workspaces/" + workspaceId;

        String mockRoutePath = httpRequest.getRequestURI().substring(workspaceUrlPrefix.length());

        // Keep only safe fields of the body for the log (413 if body > 32 KiB).
        String requestBodySample = requestBodySampler.sampleRequestBody(httpRequest);

        // Nothing after the workspace ID means the caller requested the root route.
        if (mockRoutePath.isEmpty()) {
            mockRoutePath = "/";
        }

        return mockService.executeMockRequest(
            workspaceId, httpRequest.getMethod(), mockRoutePath, requestBodySample);
    }
}
