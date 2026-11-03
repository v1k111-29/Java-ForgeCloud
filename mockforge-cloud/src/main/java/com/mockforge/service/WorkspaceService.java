package com.mockforge.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mockforge.model.Workspace;
import com.mockforge.repository.WorkspaceRepository;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository) {

        this.workspaceRepository = workspaceRepository;
    }


    // Return the available workspaces.
    public List<Workspace> getAllWorkspaces() {

        return workspaceRepository.findAllWorkspaces();
    }


    // Remove extra spaces from the name and save the workspace.
    public Workspace createWorkspace(Workspace workspace) {

        workspace.setName(workspace.getName().trim());

        return workspaceRepository.saveWorkspace(workspace);
    }


    // Return a 404 error if the workspace does not exist.
    public void checkWorkspaceExists(long workspaceId) {

        if (!workspaceRepository.workspaceExistsById(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
        }
    }
}
