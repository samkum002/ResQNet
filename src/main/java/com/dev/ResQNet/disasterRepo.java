package com.dev.ResQNet;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface disasterRepo extends MongoRepository<disasterEntity, ObjectId>{
    
    List<disasterEntity> findByAiStatusAndRetryCountLessThan(AI ai,Integer retryCount);
    List<disasterEntity> findByAiStatusAndRetryCount(AI ai,Integer retryCount);
    ObjectId  findImageByDisasterId(ObjectId disasterId);
    List<disasterEntity> findByAssignmentStatus(Assignment assignmentStatus);
    disasterEntity findByDisasterId(ObjectId disasterId);
    List<disasterEntity> findByAssignedAdminIdAndStatus(ObjectId userId, Status status);
    stationEntity findNearestStationByLocationAndStatus(GeoJsonPoint location, Station status);
    
}
