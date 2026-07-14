package com.dev.ResQNet;

import org.springframework.http.ResponseEntity;

public interface  userService {
    
    ResponseEntity<?> registration(userDTO dto);
}
