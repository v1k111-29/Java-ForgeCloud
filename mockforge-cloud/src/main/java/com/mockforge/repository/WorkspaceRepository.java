package com.mockforge.repository;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.mockforge.model.Workspace;

// All workspace SQL stays here, so the controller does not need to know the table structure.
// Uses JdbcTemplate with "?" placeholders (values sent separately = no SQL injection).
@Repository
public class WorkspaceRepository {

    private final JdbcTemplate jdbcTemplate;

    public WorkspaceRepository(JdbcTemplate jdbcTemplate) {

        this.jdbcTemplate = jdbcTemplate;
    }


    // Convert each database row into a Java object.
    // RowMapper lambda: (resultSet = current row, rowNumber = its index) -> Workspace.
    private final RowMapper<Workspace> workspaceRowMapper = (resultSet, rowNumber) -> {

        Workspace workspace = new Workspace();

        workspace.setWorkspaceId(resultSet.getLong("workspace_id"));

        workspace.setName(resultSet.getString("name"));

        workspace.setDescription(resultSet.getString("description"));

        workspace.setCreatedAt(resultSet.getObject("created_at", LocalDateTime.class));

        return workspace;
    };

    // Save a workspace and return it with the generated ID.
    // Same pattern as EndpointRepository: INSERT, grab the new id from the
    // GeneratedKeyHolder, then SELECT the row back. queryForObject expects exactly one row.
    public Workspace saveWorkspace(Workspace workspace) {

        GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(databaseConnection -> {

            PreparedStatement preparedStatement = databaseConnection.prepareStatement("INSERT INTO workspaces (name, description)"
                    + " VALUES (?, ?)", new String[]{
                "workspace_id"
            });

            preparedStatement.setString(1, workspace.getName());

            preparedStatement.setString(2, workspace.getDescription());

            return preparedStatement;
        }, generatedKeyHolder);

        String sqlQuery = "SELECT * FROM workspaces WHERE workspace_id = ?";

        return jdbcTemplate.queryForObject(sqlQuery, workspaceRowMapper, generatedKeyHolder.getKey().longValue());
    }


    // Fetch all the workspaces from the database.
    public List<Workspace> findAllWorkspaces() {

        String sqlQuery = "SELECT * FROM workspaces ORDER BY workspace_id DESC";

        return jdbcTemplate.query(sqlQuery, workspaceRowMapper);
    }


    // Check whether the given workspace exists.
    // COUNT(*) gives 0 or 1 here; Long.class says "read that single number as a Long".
    public boolean workspaceExistsById(long workspaceId) {

        String sqlQuery = "SELECT COUNT(*) FROM workspaces WHERE workspace_id = ?";

        Long workspaceCount = jdbcTemplate.queryForObject(sqlQuery, Long.class, workspaceId);

        return workspaceCount != null && workspaceCount > 0;
    }
}
