package com.mockforge.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mockforge.model.RequestLog;
import com.mockforge.repository.RequestLogRepository;
import com.mockforge.service.AsyncRequestLogger;
import com.mockforge.service.EndpointService;

@RestController
@RequestMapping("/api")
public class LogController {

    private final EndpointService endpointService;

    private final RequestLogRepository requestLogRepository;

    private final AsyncRequestLogger asyncRequestLogger;

    public LogController(EndpointService endpointService, RequestLogRepository requestLogRepository, AsyncRequestLogger asyncRequestLogger) {

        this.endpointService = endpointService;

        this.requestLogRepository = requestLogRepository;

        this.asyncRequestLogger = asyncRequestLogger;
    }


    // Fetch recent logs for the selected endpoint.
    @GetMapping("/endpoints/{endpointId}/logs")
    public List<RequestLog> getEndpointRequestLogs(@PathVariable long endpointId,
        @RequestParam(name = "limit", defaultValue = "50") int requestedLimit) {

        endpointService.getEndpointOrThrow(endpointId);

        // Keep the requested number of logs between 1 and 100.
        if (requestedLimit < 1) {
            requestedLimit = 1;
        } else if (requestedLimit > 100) {
            requestedLimit = 100;
        }

        return requestLogRepository.findRequestLogsByEndpointId(endpointId, requestedLimit);
    }


    // Fetch the logging queue size and counters.
    @GetMapping("/logging/stats")
    public Map<String, Long> getLoggingStatistics() {

        return asyncRequestLogger.getLoggingStatistics();
    }
}
