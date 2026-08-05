package com.dev.ResQNet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private userRepo repo;

    @GetMapping("/me")
    public ResponseEntity<UserInfoDto> currentUser(Authentication authentication) {

        String username = authentication.getName();

        userEntity user = repo.findByUsername(username);

        UserInfoDto dto = new UserInfoDto();
        dto.setUserId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setRoles(user.getRoles());
        return ResponseEntity.ok(dto);
        
    }
}