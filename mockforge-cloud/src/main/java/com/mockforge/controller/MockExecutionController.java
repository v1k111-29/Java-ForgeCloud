package com.mockforge.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.mockforge.service.MockService;

@RestController
public class MockExecutionController {

    private final MockService mockService;

    public MockExecutionController(MockService mockService) {
        this.mockService = mockService;
    }
    // Get the route after the workspace ID and execute the mock request.
    @RequestMapping("/mock/workspaces/{workspaceId}/**")
    public ResponseEntity<byte[]> execute(@PathVariable long workspaceId, HttpServletRequest request) {
        String prefix = request.getContextPath() + "/mock/workspaces/" + workspaceId;
        String path = request.getRequestURI().substring(prefix.length());
        return mockService.execute(workspaceId, request.getMethod(), path.isEmpty() ? "/" : path);
    }
}
