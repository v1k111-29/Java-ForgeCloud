package com.mockforge.repository;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mockforge.model.RequestLog;

@Repository
public class RequestLogRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Save a new request log to the database
    public int save(RequestLog log) {
        String sql = "INSERT INTO request_logs (endpoint_id, http_method, request_path, response_status, duration_ms, request_body_sample) VALUES (?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql,
                log.getEndpointId(),
                log.getHttpMethod(),
                log.getRequestPath(),
                log.getResponseStatus(),
                log.getDurationMs(),
                log.getRequestBodySample());
    }

    // Fetch the recent request logs for the dashboard table
    public List<RequestLog> findAll() {
        String sql = "SELECT * FROM request_logs ORDER BY received_at DESC LIMIT 50";
        RowMapper<RequestLog> rowMapper = (rs, rowNum) -> {
            RequestLog log = new RequestLog();
            log.setLogId(rs.getLong("log_id"));
            log.setEndpointId(rs.getLong("endpoint_id"));
            log.setReceivedAt(rs.getTimestamp("received_at").toLocalDateTime());
            log.setHttpMethod(rs.getString("http_method"));
            log.setRequestPath(rs.getString("request_path"));
            log.setResponseStatus(rs.getInt("response_status"));
            log.setDurationMs(rs.getInt("duration_ms"));
            log.setRequestBodySample(rs.getString("request_body_sample"));
            return log;
        };
        return jdbcTemplate.query(sql, rowMapper);
    }
}