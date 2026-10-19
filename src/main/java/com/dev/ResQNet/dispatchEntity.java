package com.dev.ResQNet;

import java.time.LocalDateTime;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection="dispatch")
public class dispatchEntity {

    public dispatchEntity() {}
    
    @Id
    private ObjectId dispatchId;
    private ObjectId stationId;
    private ObjectId disasterId;
    private Severity severity;
    private Forces forceType;
    private Integer assignedVehicle;
    private Integer assignedPersonnel;
    private Status status;
    @CreatedDate
    private LocalDateTime dispatchedAt;
    @LastModifiedDate
    private LocalDateTime completedAt;
    private ObjectId workerId;

    private List<List<Double>> routePoints;
    private Integer routeIndex = 0;

    private Double currentLatitude;
    private Double currentLongitude;

    private Boolean simulationActive = false;
    private LocalDateTime simulationStartedAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime lastLocationUpdateAt;
}
