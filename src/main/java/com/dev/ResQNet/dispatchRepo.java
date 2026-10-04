package com.dev.ResQNet;

import org.bson.types.ObjectId;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface dispatchRepo extends MongoRepository<dispatchEntity, ObjectId> {
    
    List<dispatchEntity> findByStationIdAndStatus(ObjectId stationId, Status status);
    List<dispatchEntity> findByWorkerIdAndStatus(ObjectId workerId, Status status);
    List<dispatchEntity> findByDisasterIdAndStatus(ObjectId disasterId, Status status);
}
