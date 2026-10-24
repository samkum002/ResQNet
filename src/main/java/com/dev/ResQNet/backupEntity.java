package com.dev.ResQNet;

import java.time.LocalDateTime;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection="backup")
public class backupEntity {

    public backupEntity() {}
    
    @Id 
    private ObjectId backupId;
    private Severity severity;
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
    private ObjectId workerId;
    
}
