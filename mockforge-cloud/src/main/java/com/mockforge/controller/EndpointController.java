package com.mockforge.controller;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.mockforge.model.Endpoint;
import com.mockforge.service.EndpointService;

@RestController
@RequestMapping("/api")
public class EndpointController {

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {
        this.endpointService = endpointService;
    }

    // API endpoint to fetch all endpoints.
    @GetMapping("/endpoints")
    public List<Endpoint> list() {
        return endpointService.list();
    }

    // API endpoint to fetch endpoints for a workspace.
    @GetMapping({"/endpoints/workspace/{workspaceId}", "/workspaces/{workspaceId}/endpoints"})
    public List<Endpoint> listForWorkspace(@PathVariable long workspaceId) {
        return endpointService.listForWorkspace(workspaceId);
    }

    // Validate the request and create a new endpoint.
    @PostMapping("/endpoints")
    @ResponseStatus(HttpStatus.CREATED)
    public Endpoint create(@Valid @RequestBody Endpoint endpoint) {
        return endpointService.create(endpoint);
    }

    // API endpoint to enable or disable an endpoint.
    @PatchMapping("/endpoints/{endpointId}/enabled")
    public Endpoint setEnabled(@PathVariable long endpointId, @Valid @RequestBody EnabledRequest request) {
        return endpointService.setEnabled(endpointId, request.getEnabled());
    }

    // Request body used to enable or disable an endpoint.
    public static class EnabledRequest {

        @NotNull
        private Boolean enabled;
        public Boolean getEnabled() {
            return enabled;
        }
        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }
}
