package com.dev.ResQNet;

import org.bson.types.ObjectId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController 
@RequestMapping("/dispatch")
public class backupController {

    @Autowired 
    private stationService service;
    

    @PostMapping("/backup/{dispatchId}/request")
    public ResponseEntity<?> reqBackup(@PathVariable ObjectId dispatchId, @RequestBody backupDTO dto) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        return service.requestBackup(name, dto, dispatchId);

    }

    @GetMapping("/assigned")
    public ResponseEntity<dispatches> getAssignedDispatches() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        return service.getAssignedDispatches(name);

    }
}
