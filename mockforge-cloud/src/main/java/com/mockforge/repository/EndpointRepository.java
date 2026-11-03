// Endpoint Repository is responsible for managing the persistence and retrieval of endpoint data from the database.

package com.mockforge.repository;

import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import com.mockforge.model.Endpoint;

@Repository
public class EndpointRepository {

    private final JdbcTemplate jdbcTemplate;

    public EndpointRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Convert each database row into a Java object.
    private final RowMapper<Endpoint> rowMapper = (rs, row) -> {
        Endpoint endpoint = new Endpoint();
        endpoint.setEndpointId(rs.getLong("endpoint_id"));
        endpoint.setWorkspaceId(rs.getLong("workspace_id"));
        endpoint.setEndpointName(rs.getString("name"));
        endpoint.setMockUrlPath(rs.getString("route_path"));
        endpoint.setHttpMethod(rs.getString("http_method"));
        endpoint.setResponseStatus(rs.getInt("response_status"));
        endpoint.setResponseContentType(rs.getString("response_content_type"));
        endpoint.setResponseBody(rs.getString("response_body"));
        endpoint.setEnabled(rs.getBoolean("enabled"));
        endpoint.setExecutionMode(rs.getString("execution_mode"));
        endpoint.setUpstreamUrl(rs.getString("upstream_url"));
        endpoint.setTimeoutMs(rs.getInt("timeout_ms"));
        endpoint.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return endpoint;
    };

    // Save an endpoint and return the created record.
    public Endpoint save(Endpoint endpoint) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO endpoints (workspace_id, name, route_path, http_method, response_status, response_content_type, response_body, enabled, execution_mode, upstream_url, timeout_ms)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", new String[]{"endpoint_id"});
            statement.setObject(1, endpoint.getWorkspaceId());
            statement.setObject(2, endpoint.getEndpointName());
            statement.setObject(3, endpoint.getMockUrlPath());
            statement.setObject(4, endpoint.getHttpMethod());
            statement.setObject(5, endpoint.getResponseStatus());
            statement.setObject(6, endpoint.getResponseContentType());
            statement.setObject(7, endpoint.getResponseBody());
            statement.setObject(8, endpoint.getEnabled());
            statement.setObject(9, endpoint.getExecutionMode());
            statement.setObject(10, endpoint.getUpstreamUrl());
            statement.setObject(11, endpoint.getTimeoutMs());
            return statement;
        }, keyHolder);
        return findById(keyHolder.getKey().longValue());
    }

    // Fetch all the endpoints.
    public List<Endpoint> findAll() {
        String sql = "SELECT * FROM endpoints ORDER BY endpoint_id DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    // Fetch only the endpoints belonging to the selected workspace.
    public List<Endpoint> findByWorkspaceId(long workspaceId) {
        String sql = "SELECT * FROM endpoints WHERE workspace_id = ? ORDER BY endpoint_id DESC";
        return jdbcTemplate.query(sql, rowMapper, workspaceId);
    }

    // Find one endpoint using its ID.
    public Endpoint findById(long endpointId) {
        List<Endpoint> endpoints = jdbcTemplate.query("SELECT * FROM endpoints WHERE endpoint_id = ?", rowMapper, endpointId);
        if (endpoints.isEmpty()) {
            return null;
        }
        return endpoints.get(0);
    }

    // Find an enabled endpoint using the workspace, HTTP method and path.
    public Endpoint findMatch(long workspaceId, String httpMethod, String routePath) {
        List<Endpoint> endpoints = jdbcTemplate.query("SELECT * FROM endpoints WHERE workspace_id = ? AND http_method = ? AND route_path = ? AND enabled = TRUE", rowMapper, workspaceId, httpMethod, routePath);
        if (endpoints.isEmpty()) {
            return null;
        }
        return endpoints.get(0);
    }

    // Enable or disable an existing endpoint.
    public void setEnabled(long endpointId, boolean enabled) {
        jdbcTemplate.update("UPDATE endpoints SET enabled = ? WHERE endpoint_id = ?", enabled, endpointId);
    }
}
