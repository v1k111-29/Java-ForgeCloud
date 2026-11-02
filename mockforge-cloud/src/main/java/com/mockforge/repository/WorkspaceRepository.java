package com.mockforge.repository;

import java.util.List;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mockforge.model.Workspace;

import org.springframework.web.server.ResponseStatusException;


@Repository 
public class WorkspaceRepository {
    
    @Autowired 
    private JdbcTemplate jdbcTemplate;

    // Saves a new request to Workspace database 

    public int save(Workspace workspace)
    {
        String sql = "INSERT INTO workspaces (name, description) VALUES (?,?) ";

        return jdbcTemplate.update(sql,
            workspace.getName(),
            workspace.getDescription()
        );
    }

    // Returns all the workspaces
    public List<Workspace> findAll()
    {
        String sql = "SELECT * FROM workspaces ORDER BY workspace_id DESC ";
        RowMapper<Workspace> rowMapper = (rs , rowNum) ->
        {
            Workspace work = new Workspace();   
            work.setName(rs.getString("name"));
            work.setDescription(rs.getString("description"));
            work.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            work.setWorkspaceId(rs.getLong("workspace_id"));

            return work;
        };

        return jdbcTemplate.query(sql, rowMapper);

    }   


    public boolean existsById(long workspaceId) {
        String sql = "SELECT COUNT(*) FROM workspaces WHERE workspace_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, workspaceId);

        if(!(workspaceRepository.existsByI`d(endpoint.getWorkspaceId() )))
        {

        }
            
        return count != null && count > 0;
    }


}