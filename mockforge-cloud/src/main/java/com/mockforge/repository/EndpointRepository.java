package com.mockforge.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import com.mockforge.repository.WorkspaceRepository;
import com.mockforge.model.Endpoint;

@Repository
public class EndpointRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    

    // Save a new mock endpoint to the database
    public int save(Endpoint endpoint) {
        String sql = "INSERT INTO endpoints (workspace_id, name, route_path, response_body, http_method, response_content_type, response_status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql, 
            endpoint.getWorkspaceId(), 
            endpoint.getEndpointName(), 
            endpoint.getMockUrlPath(), 
            endpoint.getResponseBody(),
            endpoint.getHttpMethod(),
            endpoint.getResponseContentType(),
            //endpoint.getEnabled(),
            endpoint.getResponseStatus()
        );
    }

    // Fetch all endpoints
    public List<Endpoint> findAll() {
        String sql = "SELECT * FROM endpoints ORDER BY created_at DESC";
        RowMapper<Endpoint> rowMapper = (rs, rowNum) -> {
            Endpoint endpoint = new Endpoint();
            endpoint.setEndpointId(rs.getLong("endpoint_id"));
            endpoint.setWorkspaceId(rs.getLong("workspace_id"));
            //endpoint.setEndpointName(rs.getLong("workspace_id") != 0 ? rs.getString("name") : null); // safe fallback or standard getter
            endpoint.setEndpointName(rs.getString("name"));
            endpoint.setMockUrlPath(rs.getString("route_path"));
            endpoint.setResponseBody(rs.getString("response_body"));
            if (rs.getTimestamp("created_at") != null) {
                endpoint.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            endpoint.setHttpMethod(rs.getString("http_method"));
            endpoint.setResponseContentType(rs.getString("response_content_type"));
            endpoint.setResponseStatus(rs.getInt("response_status"));
            endpoint.setEnabled(rs.getBoolean("enabled"));
            return endpoint;
        };
        return jdbcTemplate.query(sql, rowMapper);
    }   q3456


    // Fetch endpoints by workspace ID
    public List<Endpoint> findByWorkspaceId(Long workspaceId) {
        String sql = "SELECT * FROM endpoints WHERE workspace_id = ? ORDER BY created_at DESC";

        RowMapper<Endpoint> rowMapper = (rs, rowNum) -> {
            Endpoint endpoint = new Endpoint();
            endpoint.setEndpointId(rs.getLong("endpoint_id"));
            endpoint.setWorkspaceId(rs.getLong("workspace_id"));
            endpoint.setEndpointName(rs.getString("name"));
            endpoint.setMockUrlPath(rs.getString("route_path"));
            endpoint.setResponseBody(rs.getString("response_body"));
            if (rs.getTimestamp("created_at") != null) {
                endpoint.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            endpoint.setHttpMethod(rs.getString("http_method"));
            endpoint.setResponseContentType(rs.getString("response_content_type"));
            endpoint.setResponseStatus(rs.getInt("response_status"));
            endpoint.setEnabled(rs.getBoolean("enabled"));
            return endpoint;
        };
        return jdbcTemplate.query(sql, rowMapper, workspaceId);
    }

    




}