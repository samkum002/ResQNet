package com.dev.ResQNet;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;


@RestController
@RequestMapping("/admin")
public class adminDashboardController {
    
    @Autowired
    private adminDashboardService adminDashboardservice;

    @Autowired
    private userRepo userrepo;

    @GetMapping("/disasters")
    public ResponseEntity<List<disasterDto>> disasterAssigned(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        userEntity user = userrepo.findByUsername(name);
        if(user==null){
            ResponseEntity.notFound().build();
        }
        return adminDashboardservice.disasterList(user.getUserId());
    }
}
