package com.dev.ResQNet;

import org.bson.types.ObjectId;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface disasterRepo extends MongoRepository<disasterEntity, ObjectId>{
    
    List<disasterEntity> findByAiStatusAndRetryCountLessThan(AI ai,Integer retryCount);
    ObjectId  findImageByDisasterId(ObjectId disasterId);

    disasterEntity findByDisasterId(ObjectId disasterId);
}
