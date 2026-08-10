package com.dev.ResQNet;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/admin")
public class adminDashboardController {
    
    @Autowired
    private adminDashboardService adminDashboardservice;

    @Autowired
    private userRepo userrepo;

    @Autowired
    private disasterRepo disasterrepo;

    @GetMapping("/disasters")
    public ResponseEntity<List<disasterDto>> disasterAssigned(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        userEntity user = userrepo.findByUsername(name);
        if(user==null){
            return ResponseEntity.notFound().build();
        }
        return adminDashboardservice.disasterList(user.getUserId());
    }

    @PostMapping("/{disaterId}/approve")
    public ResponseEntity<?> approveDisaster(ObjectId disasterId){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        userEntity user = userrepo.findByUsername(name);
        if(user==null){
            return ResponseEntity.notFound().build();
        }
        return adminDashboardservice.disasterApprove(disasterId);
    }
    
}
