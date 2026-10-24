package com.dev.ResQNet;

import org.bson.types.ObjectId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface dispatchRepo extends MongoRepository<dispatchEntity, ObjectId> {
    
    List<dispatchEntity> findByStationIdAndStatus(ObjectId stationId, Status status);
    List<dispatchEntity> findByStationIdAndStatusIn(ObjectId stationId, Collection<Status> statuses);
    List<dispatchEntity> findByWorkerIdAndStatus(ObjectId workerId, Status status);
    List<dispatchEntity> findByWorkerIdAndStatusIn(ObjectId workerId, Collection<Status> statuses);
    List<dispatchEntity> findByDisasterIdAndStatus(ObjectId disasterId, Status status);
    List<dispatchEntity> findByDisasterId(ObjectId disasterId);

    List<dispatchEntity> findBySimulationActiveTrue();
}
