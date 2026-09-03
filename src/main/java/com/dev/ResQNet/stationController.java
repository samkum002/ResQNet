package com.dev.ResQNet;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/station")
public class stationController {
    
    @Autowired
    private stationService stationservice;

    @GetMapping("/missions")
    public ResponseEntity<List<dispatchDto>> getMissions(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return stationservice.getMissionsForStation(username);
    }

    @GetMapping("/{dispatchId}/approve")
    public ResponseEntity<?> approveDispatch(@PathVariable ObjectId dispatchId){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return stationservice.approveMisssion(dispatchId,username);
    }

}
