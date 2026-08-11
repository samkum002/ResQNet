package com.dev.ResQNet;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;


public interface  adminDashboardService {
    
    void checkInfo(ObjectId disasterId);
    void findAdmin(ObjectId disasterId);
    void reassignDisaster();
    void calculateFinalVal(ObjectId disasterId);
    boolean checkDuplicateDisasters(double latitude,double longitude,String state,ObjectId disasterId);
    ResponseEntity<List<disasterDto>> disasterList(ObjectId userId);
    ResponseEntity<?> disasterApprove(ObjectId disasterId);
    ResponseEntity<?> disasterReject(ObjectId disasterId);
}
