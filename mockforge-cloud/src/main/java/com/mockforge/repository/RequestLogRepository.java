package com.mockforge.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mockforge.model.RequestLog;

// The background worker writes logs here; the dashboard reads them here.
@Repository
public class RequestLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public RequestLogRepository(JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }


    // Save a request log to the database.
    // The values after sqlQuery fill the "?" placeholders in order (1st value -> 1st ?).
    // Returns how many rows were inserted (normally 1).
    public int saveRequestLog(RequestLog requestLog) {

        String sqlQuery = "INSERT INTO request_logs (endpoint_id, received_at, http_method, request_path, response_status, "
            + "duration_ms, request_body_sample, response_source, fallback_reason)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        return jdbcTemplate.update(sqlQuery,
            requestLog.getEndpointId(),
            requestLog.getReceivedAt(),
            requestLog.getHttpMethod(),
            requestLog.getRequestPath(),
            requestLog.getResponseStatus(),
            requestLog.getDurationMs(),
            requestLog.getRequestBodySample(),
            requestLog.getResponseSource(),
            requestLog.getFallbackReason());
    }


    // Convert each database row into a Java object.
    private final RowMapper<RequestLog> requestLogRowMapper = (resultSet, rowNumber) -> {

        RequestLog requestLog = new RequestLog();

        requestLog.setLogId(resultSet.getLong("log_id"));

        requestLog.setEndpointId(resultSet.getLong("endpoint_id"));

        // Read UTC session values directly without the computer's timezone conversion.
        requestLog.setReceivedAt(resultSet.getObject("received_at", LocalDateTime.class));

        requestLog.setHttpMethod(resultSet.getString("http_method"));

        requestLog.setRequestPath(resultSet.getString("request_path"));

        requestLog.setResponseStatus(resultSet.getInt("response_status"));

        // getObject(..., Integer.class) keeps SQL NULL as null (getInt would give 0).
        requestLog.setDurationMs(resultSet.getObject("duration_ms", Integer.class));

        requestLog.setRequestBodySample(resultSet.getString("request_body_sample"));

        requestLog.setResponseSource(resultSet.getString("response_source"));

        requestLog.setFallbackReason(resultSet.getString("fallback_reason"));

        return requestLog;
    };

    // Fetch the latest 50 request logs.
    public List<RequestLog> findRecentRequestLogs() {

        String sqlQuery = "SELECT * FROM request_logs ORDER BY received_at DESC, log_id DESC LIMIT 50";

        return jdbcTemplate.query(sqlQuery, requestLogRowMapper);
    }


    // Fetch recent logs belonging to one endpoint.
    // Newest first; log_id breaks ties when two logs have the same time.
    // Even the LIMIT number is a "?" placeholder (LogController keeps it 1..100).
    public List<RequestLog> findRequestLogsByEndpointId(long endpointId, int requestedLimit) {

        String sqlQuery = "SELECT * FROM request_logs WHERE endpoint_id = ? ORDER BY received_at DESC, log_id DESC LIMIT ?";

        return jdbcTemplate.query(sqlQuery, requestLogRowMapper, endpointId, requestedLimit);
    }
}
