package com.dev.ResQNet;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface resourceRepository extends MongoRepository<resourceEntity, ObjectId> {
    
}
