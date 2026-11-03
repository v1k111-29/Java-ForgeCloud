CREATE DATABASE IF NOT EXISTS mockforge_cloud;
USE mockforge_cloud;

CREATE TABLE IF NOT EXISTS workspaces (
    workspace_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workspace_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS endpoints (
    endpoint_id BIGINT NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    route_path VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    response_status SMALLINT NOT NULL,
    response_content_type VARCHAR(120) NOT NULL,
    response_body TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    execution_mode VARCHAR(20) NOT NULL DEFAULT 'MOCK',
    upstream_url VARCHAR(2000) NULL,
    timeout_ms INT NOT NULL DEFAULT 1500,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (endpoint_id),
    UNIQUE KEY uq_endpoint_workspace_method_path
        (workspace_id, http_method, route_path),
    KEY fk_endpoints_workspace (workspace_id),
    CONSTRAINT fk_endpoints_workspace
        FOREIGN KEY (workspace_id)
        REFERENCES workspaces (workspace_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS request_logs (
    log_id BIGINT NOT NULL AUTO_INCREMENT,
    endpoint_id BIGINT NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    http_method VARCHAR(10) NOT NULL,
    request_path VARCHAR(500) NOT NULL,
    response_status SMALLINT NOT NULL,
    duration_ms INT NULL,
    request_body_sample TEXT NULL,
    response_source VARCHAR(30) NOT NULL DEFAULT 'MOCK',
    fallback_reason VARCHAR(120) NULL,
    PRIMARY KEY (log_id),
    KEY idx_logs_endpoint_received (endpoint_id, received_at),
    CONSTRAINT fk_logs_endpoint
        FOREIGN KEY (endpoint_id)
        REFERENCES endpoints (endpoint_id)
        ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;