package com.dev.ResQNet;

import org.bson.types.ObjectId;

public interface  adminDashboardService {
    
    void checkInfo(ObjectId disasterId);
    void findAdmin(ObjectId disasterId);
    void reassignDisaster();
    void calculateFinalVal(ObjectId disasterId);
    
}
