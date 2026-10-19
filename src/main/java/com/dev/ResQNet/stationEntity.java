package com.dev.ResQNet;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection="stations")
public class stationEntity {

    public stationEntity() {}
    
    @Id
    private ObjectId stationId;
    private ObjectId userId;
    // private ObjectId workerId;
    private Forces forceType;
    private String stationName;
    private Station status;
    @GeoSpatialIndexed(type=GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;
    @Version
    private Long version;
    private String state;
}
