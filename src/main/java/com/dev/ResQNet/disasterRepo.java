package com.dev.ResQNet;

import org.bson.types.ObjectId;
import java.util.*;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface disasterRepo extends MongoRepository<disasterEntity, ObjectId>{
    
    List<disasterEntity> findByAiStatusAndRetryCountLessThan(AI ai,Integer retryCount);
}
