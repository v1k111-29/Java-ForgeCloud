/* Workspace.java represents one workspace in your application. 
 It holds the Java version of a row from your database’s workspaces table.
*/

package com.mockforge.model;

import java.time.LocalDateTime;

public class Workspace {

    private Long workspaceId;
    private String name;
    private String description;
    private LocalDateTime createdAt;

    
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

    public Long getWorkspace()
    {
        return workspaceId;
    }
        
}
