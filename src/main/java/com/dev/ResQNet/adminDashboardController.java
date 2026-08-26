package com.dev.ResQNet;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.parameters.RequestBody;


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

    @PostMapping("/{disasterId}/approve")
    public ResponseEntity<?> approveDisaster(@PathVariable ObjectId disasterId,@RequestBody(required=false) disasterDto dto){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        userEntity user = userrepo.findByUsername(name);
        disasterEntity disaster = disasterrepo.findByDisasterId(disasterId);
        if(user==null||disaster==null){
            return ResponseEntity.notFound().build();
        }
        if(dto!=null){
            if(dto.getSeverity()!=null){
                disaster.setSeverity(dto.getSeverity());
            }
            if(dto.getForces()!=null){
                disaster.setForces(dto.getForces());
            }
            if(dto.getDisasterType()!=null){
                disaster.setDisasterType(dto.getDisasterType());
            }
            if(dto.getSuspicious()!=null){
                disaster.setSuspicious(dto.getSuspicious());
            }
            disasterrepo.save(disaster);
        }
        return adminDashboardservice.disasterApprove(disasterId);
    }
    
    @PostMapping("/{disasterId}/reject")
    public ResponseEntity<?> rejectDisaster(@PathVariable ObjectId disasterId){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        userEntity user = userrepo.findByUsername(name);
        if(user==null){
            return ResponseEntity.notFound().build();
        }
        return adminDashboardservice.disasterReject(disasterId);
    }
}
