package com.mockforge.repository;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.mockforge.model.Endpoint;

// Save endpoint settings and turn database rows back into Endpoint objects.
@Repository
public class EndpointRepository {

    private final JdbcTemplate jdbcTemplate;

    public EndpointRepository(JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }


    // Convert each database row into a Java object.
    private final RowMapper<Endpoint> endpointRowMapper = (resultSet, rowNumber) -> {

        Endpoint endpoint = new Endpoint();

        endpoint.setEndpointId(resultSet.getLong("endpoint_id"));

        endpoint.setWorkspaceId(resultSet.getLong("workspace_id"));

        endpoint.setEndpointName(resultSet.getString("name"));

        endpoint.setMockUrlPath(resultSet.getString("route_path"));

        endpoint.setHttpMethod(resultSet.getString("http_method"));

        endpoint.setResponseStatus(resultSet.getInt("response_status"));

        endpoint.setResponseContentType(resultSet.getString("response_content_type"));

        endpoint.setResponseBody(resultSet.getString("response_body"));

        endpoint.setEnabled(resultSet.getBoolean("enabled"));

        endpoint.setExecutionMode(resultSet.getString("execution_mode"));

        endpoint.setUpstreamUrl(resultSet.getString("upstream_url"));

        endpoint.setTimeoutMs(resultSet.getInt("timeout_ms"));

        endpoint.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));

        return endpoint;
    };

    // Save an endpoint and return the created record.
    public Endpoint saveEndpoint(Endpoint endpoint) {

        GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(databaseConnection -> {

            PreparedStatement preparedStatement = databaseConnection.prepareStatement("INSERT INTO endpoints (workspace_id, name, route_path, http_method, response_status, "
                    + "response_content_type, response_body, enabled, execution_mode, upstream_url, timeout_ms)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", new String[]{
                "endpoint_id"
            });

            preparedStatement.setObject(1, endpoint.getWorkspaceId());

            preparedStatement.setObject(2, endpoint.getEndpointName());

            preparedStatement.setObject(3, endpoint.getMockUrlPath());

            preparedStatement.setObject(4, endpoint.getHttpMethod());

            preparedStatement.setObject(5, endpoint.getResponseStatus());

            preparedStatement.setObject(6, endpoint.getResponseContentType());

            preparedStatement.setObject(7, endpoint.getResponseBody());

            preparedStatement.setObject(8, endpoint.getEnabled());

            preparedStatement.setObject(9, endpoint.getExecutionMode());

            preparedStatement.setObject(10, endpoint.getUpstreamUrl());

            preparedStatement.setObject(11, endpoint.getTimeoutMs());

            return preparedStatement;
        }, generatedKeyHolder);

        return findEndpointById(generatedKeyHolder.getKey().longValue());
    }


    // Fetch all the endpoints.
    public List<Endpoint> findAllEndpoints() {

        String sqlQuery = "SELECT * FROM endpoints ORDER BY endpoint_id DESC";

        return jdbcTemplate.query(sqlQuery, endpointRowMapper);
    }


    // Fetch only the endpoints belonging to the selected workspace.
    public List<Endpoint> findEndpointsByWorkspaceId(long workspaceId) {

        String sqlQuery = "SELECT * FROM endpoints WHERE workspace_id = ? ORDER BY endpoint_id DESC";

        return jdbcTemplate.query(sqlQuery, endpointRowMapper, workspaceId);
    }


    // Find one endpoint using its ID.
    public Endpoint findEndpointById(long endpointId) {

        List<Endpoint> endpoints = jdbcTemplate.query("SELECT * FROM endpoints WHERE endpoint_id = ?", endpointRowMapper, endpointId);

        if (endpoints.isEmpty()) {
            return null;
        }

        return endpoints.get(0);
    }


    // Find an enabled endpoint using the workspace, HTTP method and path.
    public Endpoint findMatchingEndpoint(long workspaceId, String httpMethod, String routePath) {

        List<Endpoint> endpoints = jdbcTemplate.query("SELECT * FROM endpoints WHERE workspace_id = ? AND http_method = ? AND route_path = ? AND enabled = TRUE", endpointRowMapper, workspaceId, httpMethod, routePath);

        // Keep routing exact even if an older MySQL table uses a case-insensitive collation.
        for (Endpoint endpoint : endpoints) {
            if (httpMethod.equals(endpoint.getHttpMethod()) && routePath.equals(endpoint.getMockUrlPath())) {
                return endpoint;
            }
        }

        return null;
    }


    // Enable or disable an existing endpoint.
    public void updateEndpointEnabled(long endpointId, boolean enabled) {

        jdbcTemplate.update("UPDATE endpoints SET enabled = ? WHERE endpoint_id = ?", enabled, endpointId);
    }
}
