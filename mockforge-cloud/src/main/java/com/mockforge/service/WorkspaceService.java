package com.mockforge.service;

import java.util.List;
import com.mockforge.model.Workspace;
import com.mockforge.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    // Return the available workspaces.
    public List<Workspace> list() {
        return workspaceRepository.findAll();
    }

    // Remove extra spaces from the name and save the workspace.
    public Workspace create(Workspace workspace) {
        workspace.setName(workspace.getName().trim());
        return workspaceRepository.save(workspace);
    }

    // Return a 404 error if the workspace does not exist.
    public void requireExists(long workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
    }
}
