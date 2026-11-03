-- Record of the schema correction applied in MySQL Workbench for the live demo.
-- Already applied on 3 November 2026. Do not run it again on this database.
-- Existing endpoint data was preserved; the unique route index already existed.

USE mockforge_cloud;

ALTER TABLE endpoints
    RENAME COLUMN endpoint_name TO name,
    RENAME COLUMN mock_url_path TO route_path,
    ADD enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD execution_mode VARCHAR(20) NOT NULL DEFAULT 'MOCK',
    ADD upstream_url VARCHAR(2000) NULL,
    ADD timeout_ms INT NOT NULL DEFAULT 1500;
