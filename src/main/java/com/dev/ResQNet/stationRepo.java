package com.dev.ResQNet;

import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface  stationRepo extends MongoRepository<stationEntity, ObjectId>{
    
    List<stationEntity> findByStatusAndForceTypeAndStationIdNotInAndLocationNear(Station status,Forces forceType,Set<ObjectId> rejectedStationIds,GeoJsonPoint location);    
}
