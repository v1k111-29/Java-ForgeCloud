package com.mockforge.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.mockforge.model.Endpoint;
import com.mockforge.repository.EndpointRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EndpointService {

    private final EndpointRepository endpointRepository;
    private final WorkspaceService workspaceService;
    private final List<String> allowedOrigins;

    public EndpointService(EndpointRepository endpointRepository, WorkspaceService workspaceService,
            @Value("${mockforge.proxy.allowed-origins:}") List<String> allowedOrigins) {
        this.endpointRepository = endpointRepository;
        this.workspaceService = workspaceService;
        this.allowedOrigins = allowedOrigins;
    }

    // Remove one trailing slash so /products and /products/ match.
    public static String normalizePath(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    // Check the workspace and endpoint settings before saving.
    public Endpoint create(Endpoint endpoint) {
        
        workspaceService.requireExists(endpoint.getWorkspaceId());
        endpoint.setMockUrlPath(normalizePath(endpoint.getMockUrlPath()));
        
        if (endpoint.getMockUrlPath().matches(".*[?#%].*") || endpoint.getMockUrlPath().contains("//")) {
            throw badRequest("Use a plain path without query, fragment, encoding, or repeated slashes");
        }
        try {
            MediaType.parseMediaType(endpoint.getResponseContentType());
        } catch (IllegalArgumentException ex) {
            throw badRequest("Invalid response content type");
        }
        if (endpoint.getResponseBody().getBytes(StandardCharsets.UTF_8).length > 32768) {
            throw badRequest("Response body exceeds 32 KiB");
        }
        if (endpoint.getEnabled() == null) {
            endpoint.setEnabled(true);
        }
        if ("PROXY_FALLBACK".equals(endpoint.getExecutionMode())) {
            if (!"GET".equals(endpoint.getHttpMethod())) {
                throw badRequest("Proxy mode supports GET only");
            }
            validateUpstream(endpoint.getUpstreamUrl());
        }
        return endpointRepository.save(endpoint);
    }

    // Allow proxy calls only to destinations configured for this application.
    public URI validateUpstream(String url) {
        try {
            URI uri = URI.create(url);
            String origin = uri.getScheme() + "://" + uri.getRawAuthority();
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null || !allowedOrigins.contains(origin)) {
                throw badRequest("Upstream origin is not allowed");
            }
            return uri;
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw badRequest("Invalid upstream URL");
        }
    }

    // Return a 404 error if the endpoint does not exist.
    public Endpoint requireEndpoint(long endpointId) {
        Endpoint endpoint = endpointRepository.findById(endpointId);
        if (endpoint == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Endpoint not found");
        }
        return endpoint;
    }

    // Return all the endpoint definitions.
    public List<Endpoint> list() {
        return endpointRepository.findAll();
    }

    // Check the workspace and return its endpoints.
    public List<Endpoint> listForWorkspace(long workspaceId) {
        workspaceService.requireExists(workspaceId);
        return endpointRepository.findByWorkspaceId(workspaceId);
    }

    // Check that the endpoint exists before changing its enabled value.
    public Endpoint setEnabled(long endpointId, boolean enabled) {
        requireEndpoint(endpointId);
        endpointRepository.setEnabled(endpointId, enabled);
        return requireEndpoint(endpointId);
    }
    // Create a 400 response for invalid settings.
    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
