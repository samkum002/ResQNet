package com.dev.ResQNet;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;


public interface userRepo extends MongoRepository<userEntity, ObjectId> {
    
    userEntity findByUsername(String username);
}
