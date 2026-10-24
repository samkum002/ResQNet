package com.dev.ResQNet;

import org.bson.types.ObjectId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface backupRepository extends MongoRepository<backupEntity, ObjectId> {
    
    backupEntity findByDispatchId(ObjectId dispatchId);
    List<backupEntity> findByWorkerIdAndStatus(ObjectId workerId, backupStatus status);
    List<backupEntity> findByWorkerIdAndStatusIn(ObjectId workerId, Collection<backupStatus> statuses);
    List<backupEntity> findByDisasterIdAndStatus(ObjectId disasterId, backupStatus status);
    List<backupEntity> findByDisasterId(ObjectId disasterId);
    List<backupEntity> findByStationIdAndStatus(ObjectId stationId, backupStatus status);
}
