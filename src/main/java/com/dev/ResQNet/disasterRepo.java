package com.dev.ResQNet;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface disasterRepo extends MongoRepository<disasterEntity, ObjectId>{
    
}
