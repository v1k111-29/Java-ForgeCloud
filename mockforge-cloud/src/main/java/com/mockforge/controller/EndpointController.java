package com.mockforge.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mockforge.model.Endpoint;
import com.mockforge.repository.EndpointRepository;

@RestController
@RequestMapping("/api/endpoints")
public class EndpointController {

    @Autowired
    private EndpointRepository endpointRepository;

    // Get all endpoints
    @GetMapping
    public List<Endpoint> getAllEndpoints() {
        return endpointRepository.findAll();
    }

    // Create / Save a new endpoint
    @PostMapping
    public String createEndpoint(@RequestBody Endpoint endpoint) {
        endpointRepository.save(endpoint);
        return "Endpoint created successfully!";
    }
}