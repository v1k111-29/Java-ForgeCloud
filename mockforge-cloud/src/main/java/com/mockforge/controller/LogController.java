package com.mockforge.controller;

import java.util.List;
import java.util.Map;
import com.mockforge.model.RequestLog;
import com.mockforge.repository.RequestLogRepository;
import com.mockforge.service.EndpointService;
import com.mockforge.service.AsyncRequestLogger;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api")
public class LogController {

    private final EndpointService endpointService;
    private final RequestLogRepository requestLogRepository;
    private final AsyncRequestLogger requestLogger;

    public LogController(EndpointService endpointService, RequestLogRepository requestLogRepository, AsyncRequestLogger requestLogger) {
        this.endpointService = endpointService;
        this.requestLogRepository = requestLogRepository;
        this.requestLogger = requestLogger;
    }

    // Fetch recent logs for the selected endpoint.
    @GetMapping("/endpoints/{endpointId}/logs")
    public List<RequestLog> list(@PathVariable long endpointId, @RequestParam(defaultValue = "50") int limit) {
        endpointService.requireEndpoint(endpointId);
        // Keep the requested number of logs between 1 and 100.
        if (limit < 1) {
            limit = 1;
        } else if (limit > 100) {
            limit = 100;
        }

        return requestLogRepository.findByEndpoint(endpointId, limit);
    }

    // Fetch the logging queue size and counters.
    @GetMapping("/logging/stats")
    public Map<String, Long> stats() {
        return requestLogger.stats();
    }
}
