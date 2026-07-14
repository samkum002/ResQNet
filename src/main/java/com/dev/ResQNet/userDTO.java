package com.dev.ResQNet;

import org.springframework.data.mongodb.core.index.Indexed;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * userDTO
 */
@Getter
@Setter
@AllArgsConstructor

public class userDTO {

    @NotBlank(message = "Email cannot be blank")
    @Email(message="Enter valid Email")
    private String email;
    @NotBlank(message = "Enter a password")
    @Size(min=6,max=30,message="Password must be between 8 and 30")
    private String password;
    @NotBlank(message = "username cannot be blank")
    @Indexed(unique=true)
    private String username;
    
}
