package com.dev.ResQNet;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface  stationRepo extends MongoRepository<stationEntity, ObjectId>{
    
    stationEntity findNearestStationByLocationAndStatus(GeoJsonPoint location, Station status);
    
}
