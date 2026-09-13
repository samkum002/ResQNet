package com.dev.ResQNet;

import org.bson.types.ObjectId;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface userRepo extends MongoRepository<userEntity, ObjectId> {
    
    userEntity findByUsername(String username);
    userEntity findByUserId(ObjectId userId);
    List<ObjectId> findByStationIdAndWorkerStatus(ObjectId stationId, Admin workerStatus);

}
