package com.mockforge.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mockforge.model.Endpoint;

@Repository
public class EndpointRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Save a new mock endpoint to the database
    public int save(Endpoint endpoint) {
        String sql = "INSERT INTO endpoints (workspace_id, endpoint_name, mock_url_path, response_body) VALUES (?, ?, ?, ?)";
        return jdbcTemplate.update(sql, 
            endpoint.getWorkspaceId(), 
            endpoint.getEndpointName(), 
            endpoint.getMockUrlPath(), 
            endpoint.getResponseBody()
        );
    }

    // Fetch all endpoints
    public List<Endpoint> findAll() {
        String sql = "SELECT * FROM endpoints ORDER BY created_at DESC";
        RowMapper<Endpoint> rowMapper = (rs, rowNum) -> {
            Endpoint endpoint = new Endpoint();
            endpoint.setEndpointId(rs.getLong("endpoint_id"));
            endpoint.setWorkspaceId(rs.getLong("workspace_id"));
            endpoint.setEndpointName(rs.getLong("workspace_id") != 0 ? rs.getString("endpoint_name") : null); // safe fallback or standard getter
            endpoint.setEndpointName(rs.getString("endpoint_name"));
            endpoint.setMockUrlPath(rs.getString("mock_url_path"));
            endpoint.setResponseBody(rs.getString("response_body"));
            if (rs.getTimestamp("created_at") != null) {
                endpoint.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            return endpoint;
        };
        return jdbcTemplate.query(sql, rowMapper);
    }
}