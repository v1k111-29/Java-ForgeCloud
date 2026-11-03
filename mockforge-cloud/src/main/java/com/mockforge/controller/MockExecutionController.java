package com.mockforge.controller;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mockforge.service.MockService;
import com.mockforge.service.RequestBodySampler;

@RestController
public class MockExecutionController {

    private final MockService mockService;

    private final RequestBodySampler requestBodySampler;

    public MockExecutionController(MockService mockService, RequestBodySampler requestBodySampler) {

        this.mockService = mockService;

        this.requestBodySampler = requestBodySampler;
    }


    // Get the route after the workspace ID and execute the mock request.
    @RequestMapping("/mock/workspaces/{workspaceId}/**")
    public ResponseEntity<byte[]> executeMockRequest(@PathVariable long workspaceId, HttpServletRequest httpRequest) throws IOException {

        String workspaceUrlPrefix = httpRequest.getContextPath() + "/mock/workspaces/" + workspaceId;

        String mockRoutePath = httpRequest.getRequestURI().substring(workspaceUrlPrefix.length());

        String requestBodySample = requestBodySampler.sampleRequestBody(httpRequest);

        // Nothing after the workspace ID means the caller requested the root route.
        if (mockRoutePath.isEmpty()) {
            mockRoutePath = "/";
        }

        return mockService.executeMockRequest(
            workspaceId, httpRequest.getMethod(), mockRoutePath, requestBodySample);
    }
}
