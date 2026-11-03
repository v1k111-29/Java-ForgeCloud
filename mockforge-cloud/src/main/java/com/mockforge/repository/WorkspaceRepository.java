package com.mockforge.repository;

import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import com.mockforge.model.Workspace;

@Repository
public class WorkspaceRepository {

    private final JdbcTemplate jdbcTemplate;

    public WorkspaceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Convert each database row into a Java object.
    private final RowMapper<Workspace> rowMapper = (rs, row) -> {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceId(rs.getLong("workspace_id"));
        workspace.setName(rs.getString("name"));
        workspace.setDescription(rs.getString("description"));
        workspace.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return workspace;
    };

    // Save a workspace and return it with the generated ID.
    public Workspace save(Workspace workspace) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO workspaces (name, description)"
                + " VALUES (?, ?)", new String[]{"workspace_id"});
            statement.setString(1, workspace.getName());
            statement.setString(2, workspace.getDescription());
            return statement;
        }, keyHolder);
        String sql = "SELECT * FROM workspaces WHERE workspace_id = ?";
        return jdbcTemplate.queryForObject(sql, rowMapper, keyHolder.getKey().longValue());
    }

    // Fetch all the workspaces from the database.
    public List<Workspace> findAll() {
        String sql = "SELECT * FROM workspaces ORDER BY workspace_id DESC";
        return jdbcTemplate.query(sql, rowMapper);
    }

    // Check whether the given workspace exists.
    public boolean existsById(long workspaceId) {
        String sql = "SELECT COUNT(*) FROM workspaces WHERE workspace_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, workspaceId);
        return count != null && count > 0;
    }
}
