package com.mockforge.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mockforge.model.Endpoint;
import com.mockforge.model.RequestLog;
import com.mockforge.repository.EndpointRepository;
import com.mockforge.repository.RequestLogRepository;

import jakarta.servlet.http.HttpServletRequest;

@RestController
public class MockExecutionController {

    @Autowired
    private EndpointRepository endpointRepository;

    @Autowired
    private RequestLogRepository requestLogRepository;

    @RequestMapping("/mock/**")
    public ResponseEntity<String> handleMockRequest(HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        
        String requestPath = request.getRequestURI();
        String httpMethod = request.getMethod();

        List<Endpoint> endpoints = endpointRepository.findAll();
        Endpoint matchedEndpoint = null;

        for (Endpoint ep : endpoints) {
            if (requestPath.equals(ep.getMockUrlPath())) {
                matchedEndpoint = ep;
                break;
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        RequestLog log = new RequestLog();
        log.setHttpMethod(httpMethod);
        log.setRequestPath(requestPath);
        log.setReceivedAt(LocalDateTime.now());
        log.setDurationMs((int) duration);

        ResponseEntity<String> response;

        if (matchedEndpoint != null) {
            log.setEndpointId(matchedEndpoint.getEndpointId()); // Attach the valid endpoint ID!
            log.setResponseStatus(200);
            response = ResponseEntity.ok().body(matchedEndpoint.getResponseBody());
        } else {
            log.setEndpointId(1L); // Fallback default endpoint ID to prevent null constraint errors
            log.setResponseStatus(404);
            response = ResponseEntity.status(404).body("{\"error\": \"Mock endpoint not found in MockForge Cloud.\"}");
        }

        requestLogRepository.save(log);

        return response;
    }
}