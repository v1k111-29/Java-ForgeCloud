// Request Log Repository is responsible for managing the retrieval and saving of request log data from/to   the database.

package com.mockforge.repository;

import java.sql.Timestamp;
import java.util.List;
import com.mockforge.model.RequestLog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RequestLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public RequestLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Save a request log to the database.
    public int save(RequestLog log) {
        String sql = "INSERT INTO request_logs (endpoint_id, received_at, http_method, request_path, response_status, duration_ms, request_body_sample, response_source, fallback_reason)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql,
                log.getEndpointId(),
                Timestamp.valueOf(log.getReceivedAt()),
                log.getHttpMethod(),
                log.getRequestPath(),
                log.getResponseStatus(),
                log.getDurationMs(),
                log.getRequestBodySample(),
                log.getResponseSource(),
                log.getFallbackReason());
    }

    // Convert each database row into a Java object.
    private final RowMapper<RequestLog> rowMapper = (rs, row) -> {
        RequestLog log = new RequestLog();
        log.setLogId(rs.getLong("log_id"));
        log.setEndpointId(rs.getLong("endpoint_id"));
        log.setReceivedAt(rs.getTimestamp("received_at").toLocalDateTime());
        log.setHttpMethod(rs.getString("http_method"));
        log.setRequestPath(rs.getString("request_path"));
        log.setResponseStatus(rs.getInt("response_status"));
        log.setDurationMs(rs.getObject("duration_ms", Integer.class));
        log.setRequestBodySample(rs.getString("request_body_sample"));
        log.setResponseSource(rs.getString("response_source"));
        log.setFallbackReason(rs.getString("fallback_reason"));
        return log;
    };

    // Fetch the latest 50 request logs.
    public List<RequestLog> findAll() {
        String sql = "SELECT * FROM request_logs ORDER BY received_at DESC, log_id DESC LIMIT 50";
        return jdbcTemplate.query(sql, rowMapper);
    }

    // Fetch recent logs belonging to one endpoint.
    public List<RequestLog> findByEndpoint(long endpointId, int limit) {
        String sql = "SELECT * FROM request_logs WHERE endpoint_id = ? ORDER BY received_at DESC, log_id DESC LIMIT ?";
        return jdbcTemplate.query(sql, rowMapper, endpointId, limit);
    }
}
