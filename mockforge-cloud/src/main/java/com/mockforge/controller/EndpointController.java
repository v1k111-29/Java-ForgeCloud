package com.mockforge.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.mockforge.model.Endpoint;
import com.mockforge.service.EndpointService;

// REST API for managing endpoint definitions (JSON in, JSON out).
// @RequestMapping("/api") at class level = every route below starts with /api.
// Returned Java objects (Endpoint, List<Endpoint>) are turned into JSON automatically.
@RestController
@RequestMapping("/api")
public class EndpointController {

    private final EndpointService endpointService;

    public EndpointController(EndpointService endpointService) {

        this.endpointService = endpointService;
    }


    // API endpoint to fetch all endpoints.
    @GetMapping("/endpoints")
    public List<Endpoint> getAllEndpoints() {

        return endpointService.getAllEndpoints();
    }


    // API endpoint to fetch endpoints for a workspace.
    // Two URLs in { } = both routes call this same method.
    @GetMapping({
        "/endpoints/workspace/{workspaceId}", "/workspaces/{workspaceId}/endpoints"
    })
    public List<Endpoint> getEndpointsByWorkspaceId(@PathVariable long workspaceId) {

        return endpointService.getEndpointsByWorkspaceId(workspaceId);
    }


    // Validate the request and create a new endpoint.
    // @RequestBody: turn the JSON body into an Endpoint object.
    // @Valid: check the rules on Endpoint's fields first; if broken -> 400
    //         (handled by ApiExceptionHandler). @ResponseStatus(CREATED) = reply 201.
    @PostMapping("/endpoints")
    @ResponseStatus(HttpStatus.CREATED)
    public Endpoint createEndpoint(@Valid @RequestBody Endpoint endpoint) {

        return endpointService.createEndpoint(endpoint);
    }


    // The document's create route takes workspace ownership from the URL.
    // No @Valid here on purpose: workspaceId is filled in from the URL first,
    // then EndpointService.createEndpoint runs the validator itself.
    // Note: getWorkspaceId() is a Long (object); "!=" with a long unboxes it to compare numbers.
    @PostMapping("/workspaces/{workspaceId}/endpoints")
    @ResponseStatus(HttpStatus.CREATED)
    public Endpoint createEndpointInWorkspace(@PathVariable long workspaceId, @RequestBody Endpoint endpoint) {

        if (endpoint.getWorkspaceId() != null && endpoint.getWorkspaceId() != workspaceId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Body workspaceId must match the URL");
        }

        endpoint.setWorkspaceId(workspaceId);

        return endpointService.createEndpoint(endpoint);
    }


    // API endpoint to enable or disable an endpoint.
    // PATCH = change part of a resource. Body example: {"enabled": false}
    @PatchMapping("/endpoints/{endpointId}/enabled")
    public Endpoint updateEndpointEnabled(@PathVariable long endpointId, @Valid @RequestBody EnabledRequest enabledRequest) {

        return endpointService.updateEndpointEnabled(endpointId, enabledRequest.getEnabled());
    }


    // Request body used to enable or disable an endpoint.
    // Boolean (object) instead of boolean so a missing value is null, and
    // @NotNull then rejects it with 400 instead of silently meaning false.
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
