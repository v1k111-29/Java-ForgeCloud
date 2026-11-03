package com.mockforge.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.mockforge.model.Workspace;
import com.mockforge.service.WorkspaceService;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    // API endpoint to fetch the workspaces.
    @GetMapping
    public List<Workspace> list() {
        return workspaceService.list();
    }

    // API endpoint to create a workspace.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Workspace create(@Valid @RequestBody Workspace workspace) {
        return workspaceService.create(workspace);
    }
}
