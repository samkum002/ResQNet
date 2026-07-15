package com.dev.ResQNet;

import java.time.LocalDateTime;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Document(collection="dispatch")
public class dispatchEntity {
    
    @Id
    private ObjectId dispatchId;
    private ObjectId stationId;
    private Integer assignedVehicle;
    private Integer assignedPersonnel;
    private Status status;
    @CreatedDate
    private LocalDateTime dispatchedAt;
    @LastModifiedDate
    private LocalDateTime completedAt;
}
