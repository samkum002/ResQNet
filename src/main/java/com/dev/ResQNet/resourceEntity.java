package com.dev.ResQNet;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.domain.Persistable;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Document(collection="resources")
public class resourceEntity implements Persistable<ObjectId> {

    public resourceEntity() {}
    
    @Id
    public ObjectId resourceId;
    public ObjectId stationId;
    public Integer availablePersonnel;
    public Integer availableVehicle;
    public Integer totalPersonnel;
    public Integer totalVehicle;
    @Version
    private Long version;

    @Override
    @JsonIgnore
    public ObjectId getId() {
        return resourceId;
    }

    @Override
    @JsonIgnore
    public boolean isNew() {
        return resourceId == null;
    }
}
