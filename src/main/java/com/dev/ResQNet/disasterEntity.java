package com.dev.ResQNet;

import java.time.LocalDateTime;
import java.util.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Document(collection="disaster")
public class disasterEntity {
    
    @Id
    private ObjectId disasterId;
    private ObjectId userId;
    private Status status;
    private AI aiStatus;
    private Severity severity;
    @GeoSpatialIndexed(type=GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;
    private Integer aiConfidence;
    private Integer finalConfidence;
    @CreatedDate
    private LocalDateTime dispatchedAt;
    @LastModifiedDate
    private LocalDateTime completedAt;
    private Boolean suspicious;
    private String state;
    private ObjectId assignedAdminId;
    private Integer reportCount;
    private Boolean reAssigned;
    private ObjectId stationId;
    private Set<Disaster> disasterType = new HashSet<>();
    private Set<Forces> forces = new HashSet<>();
    private ObjectId image;
}
