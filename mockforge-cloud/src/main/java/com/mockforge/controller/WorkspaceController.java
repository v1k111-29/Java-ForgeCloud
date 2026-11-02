package com.mockforge.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import com.mockforge.model.Workspace;
import com.mockforge.repository.WorkspaceRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.List;

@RestController 
@RequestMapping("/api")
public class WorkspaceController {

    @Autowired
    private WorkspaceRepository workspaceRepository;

    // API endpoint to fetch the workspaces
    @GetMapping("/workspaces")
    public List<Workspace> getWorkSpaces() {
        return workspaceRepository.findAll();
    }
    
}
