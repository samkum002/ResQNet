package com.dev.ResQNet;

import java.time.LocalDateTime;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
public class userServiceImpl implements userService{
    
    @Autowired
    private userRepo repo;

    @Autowired
    PasswordEncoder encoder;

    @Override
    public ResponseEntity<?> registration(userDTO dto){
        
        if(repo.findByUsername(dto.getUsername())!=null){
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Username is already taken");
        }
        userEntity entity = new userEntity();
        entity.setUsername(dto.getUsername());
        entity.setPassword(encoder.encode(dto.getPassword()));
        entity.setEmail(dto.getEmail());
        entity.setRoles(Arrays.asList("USER"));
        entity.setTrustScore(50);
        // entity.setAdminState("UTTAR PRADESH");
        // entity.setActiveIncidents(0);
        entity.setCreatedAt(LocalDateTime.now());
        // entity.setAdminStatus(Admin.AVAILABLE);
        repo.save(entity);
        return ResponseEntity.ok("User registered successfully");
    }

    
}
