package com.dev.ResQNet;

import org.bson.types.ObjectId;
import java.util.List;
import org.springframework.http.ResponseEntity;


public interface  adminDashboardService {
    
    void checkInfo(ObjectId disasterId);
    void findAdmin(ObjectId disasterId);
    void reassignDisaster();
    void calculateFinalVal(ObjectId disasterId);
    boolean checkDuplicateDisasters(double latitude,double longitude,String state,ObjectId disasterId);
    ResponseEntity<List<disasterDto>> disasterList(ObjectId userId);
}
