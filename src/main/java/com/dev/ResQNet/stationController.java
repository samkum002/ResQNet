package com.dev.ResQNet;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/station")
public class stationController {
    
    @Autowired
    private stationService stationservice;

    @GetMapping("/{stationId}/missions")
    private ResponseEntity<List<stationEntity>> getMissions(@PathVariable ObjectId stationId){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        // Check if the stationId belongs to the authenticated user
        return ResponseEntity.ok(stationservice.getMissionsForStation(stationId, username));
    }

}
