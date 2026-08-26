package com.dev.ResQNet;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.*;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "users")
public class userEntity {
    
    @Id
    private ObjectId userId;
    // private ObjectId stationId;
    private String email;
    private String password;
    private List<String> roles = new ArrayList<>();
    private String username;
    private Integer trustScore;
    @CreatedDate
    private LocalDateTime createdAt;
    private String adminState;
    private Integer activeIncidents;
    private Admin adminStatus;

}
