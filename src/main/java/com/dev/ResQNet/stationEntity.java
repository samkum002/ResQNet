package com.dev.ResQNet;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection="stations")
public class stationEntity implements Persistable<ObjectId> {

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

    @Override
    @JsonIgnore
    public ObjectId getId() {
        return stationId;
    }

    @Override
    @JsonIgnore
    public boolean isNew() {
        return stationId == null;
    }
}
