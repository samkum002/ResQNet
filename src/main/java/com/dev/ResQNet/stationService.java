package com.dev.ResQNet;

import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
public interface stationService {
    
    void findStation(ObjectId disasterId, Integer newTrucks, Integer newPersonnel);
    public ResponseEntity<stationDashboard> getMissionsForStation(String username);
    public ResponseEntity<?> approveMisssion(ObjectId dispatchId, String username);
    public void newMission(ObjectId dispatchId, Forces force);
    ResponseEntity<?> completeMission(ObjectId dispatchId, String username);
    ResponseEntity<?> fakeMission(ObjectId dispatchId, String username);
    ResponseEntity<?> requestBackup(String name, backupDTO dto, ObjectId dispatchId);
    ResponseEntity<dispatches> getAssignedDispatches(String name);
    public void completeDisasterNormal(ObjectId disasterId);
    public void completeDisasterReject(ObjectId disasterId);
    public void completeDisasterBackup(ObjectId disasterId);
    ResponseEntity<?> completeDispatch(ObjectId dispatchId, String username);
    ResponseEntity<?> falseDispatch(ObjectId dispatchId, String username);
    ResponseEntity<?> startDispatch(ObjectId dispatchId, String username);

    /**
     * Worker submits a recall request for a specific dispatch.
     *
     * @param username   authenticated worker's username (from Security context)
     * @param dispatchId the dispatch to recall
     * @param reason     human-readable reason supplied by the worker
     */
    ResponseEntity<?> requestRecall(String username, ObjectId dispatchId, String reason);

    /**
     * Station manager approves a pending recall request.
     * Stops the simulation, restores resources, and sets dispatch to RECALLED.
     *
     * @param username authenticated manager's username
     * @param recallId the recall request to approve
     */
    ResponseEntity<?> approveRecall(String username, ObjectId recallId);

    /**
     * Station manager rejects a pending recall request.
     * Dispatch continues unchanged.
     *
     * @param username authenticated manager's username
     * @param recallId the recall request to reject
     */
    ResponseEntity<?> rejectRecall(String username, ObjectId recallId);

    /**
     * Get all pending recall requests for the station managed by the specified manager.
     */
    ResponseEntity<?> getPendingRecallsForManager(String username);

    /**
     * Get active (e.g. PENDING) recall requests submitted by the specified worker.
     */
    ResponseEntity<?> getActiveRecallsForWorker(String username);
}
