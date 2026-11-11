package com.mockforge.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mockforge.model.Workspace;
import com.mockforge.service.WorkspaceService;

// REST API for workspaces (a workspace is a folder that groups endpoints).
// Base URL for everything here: /api/workspaces
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {

        this.workspaceService = workspaceService;
    }


    // API endpoint to fetch the workspaces.
    @GetMapping
    public List<Workspace> getAllWorkspaces() {

        return workspaceService.getAllWorkspaces();
    }


    // API endpoint to create a workspace.
    // @Valid + @RequestBody: read JSON into Workspace and check its rules (400 if bad).
    // Success returns 201 Created with the saved workspace (including its new id).
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Workspace createWorkspace(@Valid @RequestBody Workspace workspace) {

        return workspaceService.createWorkspace(workspace);
    }
}
