package com.dev.ResQNet;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import org.springframework.security.crypto.password.PasswordEncoder;


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
        entity.setCreatedAt(LocalDateTime.now());
        userEntity saved = repo.save(entity);
        System.out.println(saved.getUserId());
        // repo.save(entity);
        return ResponseEntity.ok("User registered successfully");
    }

    
}
