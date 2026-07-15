package com.dev.ResQNet;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Document(collection="resources")
public class resourceEntity {
    
    @Id
    public ObjectId resourceId;
    public ObjectId stationId;
    public Integer availablePersonnel;
    public Integer availableVehicle;
}
