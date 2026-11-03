//  Holds the list of workspaces and their details, including name, description, and creation date. 



package com.mockforge.model;

import java.time.LocalDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class Workspace {

    private Long workspaceId;

    @NotBlank // Required field; spaces alone are not allowed.
    @Size(max = 120) // Must fit in the database column.
    private String name;

    @Size(max = 500)
    private String description;
    private LocalDateTime createdAt;

    // Getters and Setters
    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
