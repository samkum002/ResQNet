package com.dev.ResQNet;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface  adminDashboardRepo extends MongoRepository<userEntity, ObjectId>{
    
    List<userEntity> findByRolesContainingAndAdminStateAndAdminStatus(String roles,String adminState,Admin adminStatus);
    List<userEntity> findByRolesContainingAndAdminState(String roles,String adminState);
}
