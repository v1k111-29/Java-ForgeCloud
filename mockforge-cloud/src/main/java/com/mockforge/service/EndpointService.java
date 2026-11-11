package com.mockforge.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mockforge.model.Endpoint;
import com.mockforge.repository.EndpointRepository;

// Business rules for endpoints: validate the input, then let the repository save/read.
// Controllers call this class; this class calls EndpointRepository (layers:
// controller -> service -> repository).
@Service
public class EndpointService {

    private final EndpointRepository endpointRepository;

    private final WorkspaceService workspaceService;

    private final List<String> allowedUpstreamOrigins;

    private final Validator validator;

    // allowedUpstreamOrigins comes from the setting mockforge.proxy.allowed-origins,
    // a comma-separated list like "http://localhost:9001". Default is empty = nothing allowed.
    // Validator checks the @NotBlank / @Size / ... rules written on the Endpoint fields.
    public EndpointService(EndpointRepository endpointRepository,
        WorkspaceService workspaceService,
        @Value("${mockforge.proxy.allowed-origins:}") List<String> allowedUpstreamOrigins,
        Validator validator) {

        this.endpointRepository = endpointRepository;

        this.workspaceService = workspaceService;

        this.allowedUpstreamOrigins = allowedUpstreamOrigins;

        this.validator = validator;
    }


    // Treat /products and /products/ as the same route. Keep the root / unchanged.
    // Examples: "/products/" -> "/products", "/" -> "/", "/a//" -> "/a/" (only ONE slash removed).
    // static = can be called without an object: EndpointService.normalizePath(...).
    public static String normalizePath(String routePath) {

        if (routePath.length() > 1 && routePath.endsWith("/")) {
            return routePath.substring(0, routePath.length() - 1);
        }

        return routePath;
    }


    // Check the endpoint settings before asking the repository to save them.
    public Endpoint createEndpoint(Endpoint endpoint) {

        // The nested API fills in workspaceId from the URL before reaching this check.
        Set<ConstraintViolation<Endpoint>> validationErrors = validator.validate(endpoint);

        if (!validationErrors.isEmpty()) {
            throw new ConstraintViolationException(validationErrors);
        }

        // 404 if the workspace id does not exist.
        workspaceService.checkWorkspaceExists(endpoint.getWorkspaceId());

        endpoint.setMockUrlPath(normalizePath(endpoint.getMockUrlPath()));

        String routePath = endpoint.getMockUrlPath();

        // Regex ".*[?#%].*" = "contains ? or # or % anywhere".
        // So "/items?id=1", "/a#top", "/a%20b" and "/a//b" are all rejected with 400.
        if (routePath.matches(".*[?#%].*") || routePath.contains("//")) {
            throw createBadRequestException(
                "Use a plain path without query, fragment, encoding, or repeated slashes");
        }

        // Make sure the content type looks valid, e.g. "application/json".
        try {
            MediaType.parseMediaType(endpoint.getResponseContentType());
        } catch (IllegalArgumentException exception) {
            throw createBadRequestException("Invalid response content type");
        }

        if (endpoint.getResponseBody().getBytes(StandardCharsets.UTF_8).length > 32768) {
            throw createBadRequestException("Response body exceeds 32 KiB");
        }

        // 32768 bytes = 32 KiB. We count bytes (UTF-8), not characters.
        // If the client did not send "enabled", new endpoints start enabled.
        if (endpoint.getEnabled() == null) {
            endpoint.setEnabled(true);
        }

        if ("PROXY_FALLBACK".equals(endpoint.getExecutionMode())) {
            if (!"GET".equals(endpoint.getHttpMethod())) {
                throw createBadRequestException("Proxy mode supports GET only");
            }

            validateUpstreamUrl(endpoint.getUpstreamUrl());
        }

        return endpointRepository.saveEndpoint(endpoint);
    }


    // The upstream URL must belong to an origin approved in application settings.
    // Origin = scheme + "://" + host[:port], e.g. "http://localhost:9001/api/x" -> "http://localhost:9001".
    // Also rejected: non-http(s) schemes, missing host, "user:pass@" parts, and "#fragment".
    // Returning the URI lets MockService use the already-checked value.
    public URI validateUpstreamUrl(String upstreamUrl) {

        try {
            URI upstreamUri = URI.create(upstreamUrl);

            String upstreamOrigin = upstreamUri.getScheme() + "://" + upstreamUri.getRawAuthority();

            boolean supportedProtocol = "http".equals(upstreamUri.getScheme())
                || "https".equals(upstreamUri.getScheme());

            if (!supportedProtocol
                    || upstreamUri.getHost() == null
                    || upstreamUri.getUserInfo() != null
                    || upstreamUri.getFragment() != null
                    || !allowedUpstreamOrigins.contains(upstreamOrigin)) {

                throw createBadRequestException("Upstream origin is not allowed");
            }

            return upstreamUri;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw createBadRequestException("Invalid upstream URL");
        }
    }


    // Return the endpoint, or stop with a 404 when its ID is unknown.
    public Endpoint getEndpointOrThrow(long endpointId) {

        Endpoint endpoint = endpointRepository.findEndpointById(endpointId);

        if (endpoint == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Endpoint not found");
        }

        return endpoint;
    }


    // Fetch every endpoint definition saved in the database.
    public List<Endpoint> getAllEndpoints() {

        return endpointRepository.findAllEndpoints();
    }


    // Check the workspace first so an unknown ID returns 404 instead of an empty list.
    public List<Endpoint> getEndpointsByWorkspaceId(long workspaceId) {

        workspaceService.checkWorkspaceExists(workspaceId);

        return endpointRepository.findEndpointsByWorkspaceId(workspaceId);
    }


    // Read the updated row so the caller receives the endpoint's current settings.
    public Endpoint updateEndpointEnabled(long endpointId, boolean enabled) {

        getEndpointOrThrow(endpointId);

        endpointRepository.updateEndpointEnabled(endpointId, enabled);

        return getEndpointOrThrow(endpointId);
    }


    // Small helper: builds (does not throw) a 400 Bad Request error with a message.
    private ResponseStatusException createBadRequestException(String errorMessage) {

        return new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
    }
}
