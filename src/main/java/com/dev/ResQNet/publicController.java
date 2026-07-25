package com.dev.ResQNet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/public")
public class publicController {
    
    @Autowired
    private userService service;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody userDTO dto){
        return service.registration(dto);
    }
}
