-- Run once ONLY for an existing database. Do not run after creating the new schema.
USE mockforge_cloud;
-- If your table is still named mock_endpoints, rename it before continuing:
-- RENAME TABLE mock_endpoints TO endpoints;
-- These ALTER statements expect the original project schema (name and route_path columns).
ALTER TABLE endpoints
    MODIFY route_path VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    ADD execution_mode VARCHAR(20) NOT NULL DEFAULT 'MOCK',
    ADD upstream_url VARCHAR(2000) NULL,
    ADD timeout_ms INT NOT NULL DEFAULT 1500;
ALTER TABLE request_logs
    ADD response_source VARCHAR(30) NOT NULL DEFAULT 'MOCK',
    ADD fallback_reason VARCHAR(120) NULL;
