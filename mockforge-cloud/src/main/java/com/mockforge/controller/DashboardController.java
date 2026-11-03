package com.mockforge.controller;

import com.mockforge.model.RequestLog;
import com.mockforge.repository.RequestLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {
    @Autowired
    private RequestLogRepository requestLogRepository;

    // API endpoint to fetch the recent request logs for the dashboard table

    @GetMapping("/logs")

    public List<RequestLog> getRequestLogs() {
        return requestLogRepository.findAll();
    }
}
