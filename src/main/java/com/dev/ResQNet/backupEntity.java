package com.dev.ResQNet;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@Document(collection="backup")
public class backupEntity {
    
    @Id 
    private ObjectId backupId;
    private ObjectId disasterId;
    private ObjectId stationId;
    private ObjectId dispatchId;
    private Forces force;
    private Integer reqVehicles;
    private Integer reqPersonnel;
    private backupStatus status;
    @CreatedDate
    private LocalDateTime requestedAt;
    @LastModifiedDate
    private LocalDateTime AssingnedAt;
    
}
