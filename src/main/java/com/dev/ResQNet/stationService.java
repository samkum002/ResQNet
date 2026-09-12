package com.dev.ResQNet;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
public interface stationService {
    
    void findStation(ObjectId disasterId, Integer newTrucks, Integer newPersonnel);
    public ResponseEntity<List<dispatchDto>> getMissionsForStation(String username);

    public ResponseEntity<?> approveMisssion(ObjectId dispatchId, String username);

    public void newMission(ObjectId dispatchId, Forces force);
    ResponseEntity<?> completeMission(ObjectId dispatchId, String username);
    ResponseEntity<?> fakeMission(ObjectId dispatchId, String username);
    ResponseEntity<?> requestBackup(String name, backupDTO dto, ObjectId dispatchId);
}
