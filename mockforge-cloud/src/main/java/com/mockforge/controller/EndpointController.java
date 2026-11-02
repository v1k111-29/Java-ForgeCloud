package com.mockforge.controller;
import java.util.List;

// Spring framework annotations and classes
import org.springframework.beans.factory.annotation.Autowired;

// HTTP and REST annotations
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;


import org.springframework.http.HttpStatus;

// Model and Repository imports for endpoints
import com.mockforge.model.Endpoint;
import com.mockforge.repository.EndpointRepository;
import com.mockforge.repository.WorkspaceRepository;

// for endpoint name validation 
import jakarta.validation.Valid;





@RestController
@RequestMapping("/api/endpoints")
public class EndpointController {

    @Autowired 
    private final EndpointRepository endpointRepository;
    private final WorkspaceRepository workspaceRepository;
    
    
    

    EndpointController(EndpointRepository endpointRepository , WorkspaceRepository workspaceRepository  ) {
        this.endpointRepository = endpointRepository;
        this.workspaceRepository = workspaceRepository;   
    }

    // Get all endpoints
    @GetMapping
    public List<Endpoint> getAllEndpoints() {
        return endpointRepository.findAll();
    }

    // Create / Save a new endpoint
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String createEndpoint(@RequestBody @Valid Endpoint endpoint) {
        endpointRepository.save(endpoint);
        return "Endpoint created successfully!";
        
    }


    // fetch endpoints by workspace id
    @GetMapping("/workspace/{workspaceid}")
    public List<Endpoint>  getEndpointsByWorkspaceId(@PathVariable Long workspaceid) {
        return endpointRepository.findByWorkspaceId(workspaceid);
    }
    
}
    
