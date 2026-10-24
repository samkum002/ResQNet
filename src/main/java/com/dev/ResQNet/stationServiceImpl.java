package com.dev.ResQNet;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class stationServiceImpl implements stationService {
    
    @Autowired
    private stationRepo stationRepository;

    @Autowired
    private DispatchSimulationService simulationService;

    @Autowired 
    private backupRepository backupRepo;

    @Autowired
    private resourceRepository resourceRepo;

    @Autowired
    private dispatchRepo dispatchRepository;

    @Autowired
    private userRepo userRepository;

    @Autowired
    private disasterRepo disasterRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public ResponseEntity<?> startDispatch(ObjectId dispatchId,String username) {

        userEntity worker = userRepository.findByUsername(username);

        if (worker == null) {
            return ResponseEntity.status(404).body("Worker not found.");
        }

        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElse(null);

        if (dispatch == null) {
            return ResponseEntity.status(404).body("Dispatch not found.");
        }

        if (dispatch.getWorkerId() == null || !dispatch.getWorkerId().equals(worker.getUserId())) {
            return ResponseEntity.status(403).body("This dispatch is not assigned to you.");
        }

        boolean backup = dispatch.getStatus() == Status.BACKUP_ASSIGNED;
        boolean normal = dispatch.getStatus() == Status.DISPATCH_ASSIGNED;

        if (!backup && !normal) {
            return ResponseEntity.badRequest().body("Dispatch is not in an assigned state.");
        }

        backupEntity backupRecord = null;

        if (backup) {
            backupRecord = backupRepo.findByDispatchId(dispatchId);

            if (backupRecord == null) {
                return ResponseEntity.status(404).body("Associated backup record not found.");
            }
        }

        List<List<Double>> routePoints;

        try {
            routePoints = simulationService.calculateRoute(dispatchId);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(502).body(ex.getMessage());
        } catch (Exception ex) {
            return ResponseEntity.status(502).body(ex.getMessage() != null ? ex.getMessage() : "Could not calculate a route. Please retry.");
        }

        Status previousStatus = dispatch.getStatus();
        LocalDateTime previousDispatchedAt = dispatch.getDispatchedAt();
        backupStatus previousBackupStatus = backupRecord == null ? null : backupRecord.getStatus();

        try {
            dispatch.setStatus(backup ? Status.BACKUP_DISPATCHED : Status.DISPATCHED);

            if (!backup) {
                dispatch.setDispatchedAt(LocalDateTime.now());
            }

            dispatchRepository.save(dispatch);

            if (backupRecord != null) {
                backupRecord.setStatus(backupStatus.DISPATCHED);
                backupRepo.save(backupRecord);
            }

            simulationService.start(dispatchId, routePoints);

            return ResponseEntity.ok("Dispatch simulation started.");

        } catch (Exception ex) {
            dispatchEntity latestDispatch = dispatchRepository.findById(dispatchId).orElse(null);
            boolean anotherStartSucceeded = latestDispatch != null
                    && latestDispatch.getArrivedAt() != null
                    || simulationService.isStartingOrRunning(dispatchId);

            if (!anotherStartSucceeded) {
                dispatch.setStatus(previousStatus);
                dispatch.setDispatchedAt(previousDispatchedAt);
                dispatchRepository.save(dispatch);

                if (backupRecord != null) {
                    backupRecord.setStatus(previousBackupStatus);
                    backupRepo.save(backupRecord);
                }
            }

            return ResponseEntity.internalServerError().body("Could not start dispatch simulation.");
        }
    }

    private boolean hasDispatchArrived(dispatchEntity dispatch) {
        if (dispatch == null) {
            return false;
        }
        if (dispatch.getArrivedAt() != null) {
            return true;
        }
        if (dispatch.getRoutePoints() != null && !dispatch.getRoutePoints().isEmpty()
                && dispatch.getRouteIndex() != null
                && dispatch.getRouteIndex() >= dispatch.getRoutePoints().size()) {
            return true;
        }
        return false;
    }

    @Override
    public ResponseEntity<?> completeDispatch(ObjectId dispatchId, String username){

        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElseThrow(() -> new RuntimeException("Dispatch not found"));
        userEntity worker = userRepository.findByUsername(username);

        if(dispatch.getStatus() == Status.BACKUP_DISPATCHED){

            backupEntity backup = backupRepo.findByDispatchId(dispatchId);

            if (!dispatch.getStationId().equals(worker.getStationId())){
                return ResponseEntity.status(403).body("You are not authorized to complete this mission.");
            }
            if (!hasDispatchArrived(dispatch)) {
                return ResponseEntity.badRequest().body("Unit has not yet arrived at incident destination.");
            }
            if (dispatch.getStatus() != Status.BACKUP_DISPATCHED) {
                return ResponseEntity.badRequest().body("This mission is not in a state that can be completed.");
            }

            dispatch.setStatus(Status.BACKUP_DISPATCH_COMPLETED);
            dispatchRepository.save(dispatch);

            if (backup != null) {
                backup.setStatus(backupStatus.BACKUP_DISPATCH_COMPLETED);
                backupRepo.save(backup);
            }

            stationEntity station = stationRepository.findById(dispatch.getStationId()).orElse(null);
            userEntity adminUser = (station != null && station.getUserId() != null) ? userRepository.findById(station.getUserId()).orElse(null) : null;
            if (adminUser == null && station != null && station.getStationId() != null) {
                adminUser = userRepository.findAll().stream()
                        .filter(u -> station.getStationId().equals(u.getStationId()) && u.getRoles() != null && u.getRoles().contains("STATION_MANAGER"))
                        .findFirst().orElse(null);
            }
            dispatchDto dto = new dispatchDto(dispatch.getSeverity(), dispatch.getDispatchId().toHexString(), dispatch.getForceType(), dispatch.getAssignedVehicle(), dispatch.getAssignedPersonnel(), dispatch.getStatus());
            if (adminUser != null && adminUser.getUsername() != null) {
                messagingTemplate.convertAndSendToUser(adminUser.getUsername(), "/update/mission", dto);
            }
            messagingTemplate.convertAndSend("/update/mission", dto);

            return ResponseEntity.ok("Backup Dispatch completed successfully.");
        }

        if (!dispatch.getStationId().equals(worker.getStationId())){
            return ResponseEntity.status(403).body("You are not authorized to complete this mission.");
        }
        if (!hasDispatchArrived(dispatch)) {
            return ResponseEntity.badRequest().body("Unit has not yet arrived at incident destination.");
        }
        if (dispatch.getStatus() != Status.DISPATCHED) {
            return ResponseEntity.badRequest().body("This mission is not in a state that can be completed.");
        }

        dispatch.setStatus(Status.DISPATCH_COMPLETED);
        dispatchRepository.save(dispatch);

        stationEntity station = stationRepository.findById(dispatch.getStationId()).orElse(null);
        userEntity adminUser = (station != null && station.getUserId() != null) ? userRepository.findById(station.getUserId()).orElse(null) : null;
        if (adminUser == null && station != null && station.getStationId() != null) {
            adminUser = userRepository.findAll().stream()
                    .filter(u -> station.getStationId().equals(u.getStationId()) && u.getRoles() != null && u.getRoles().contains("STATION_MANAGER"))
                    .findFirst().orElse(null);
        }
        dispatchDto dto = new dispatchDto(dispatch.getSeverity(), dispatch.getDispatchId().toHexString(), dispatch.getForceType(), dispatch.getAssignedVehicle(), dispatch.getAssignedPersonnel(), dispatch.getStatus());
        if (adminUser != null && adminUser.getUsername() != null) {
            messagingTemplate.convertAndSendToUser(adminUser.getUsername(), "/update/mission", dto);
        }
        messagingTemplate.convertAndSend("/update/mission", dto);
        
        return ResponseEntity.ok("Dispatch completed successfully.");
    }

    @Override
    public ResponseEntity<?> falseDispatch(ObjectId dispatchId, String username){

        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElseThrow(() -> new RuntimeException("Dispatch not found"));
        userEntity worker = userRepository.findByUsername(username);

        if (!dispatch.getStationId().equals(worker.getStationId())){
            return ResponseEntity.status(403).body("You are not authorized to mark this mission as fake.");
        }

        if (!hasDispatchArrived(dispatch)) {
            return ResponseEntity.badRequest().body("Unit has not yet arrived at incident destination.");
        }

        boolean isBackup = dispatch.getStatus() == Status.BACKUP_DISPATCHED;
        if (dispatch.getStatus() != Status.DISPATCHED && !isBackup) {
            return ResponseEntity.badRequest().body("This mission is not in a state that can be marked as fake.");
        }

        if (isBackup) {
            backupEntity backup = backupRepo.findByDispatchId(dispatchId);
            if (backup != null) {
                backup.setStatus(backupStatus.FAILED);
                backupRepo.save(backup);
            }
        }

        dispatch.setStatus(Status.DISPATCH_FALSE);
        dispatchRepository.save(dispatch);

        stationEntity station = stationRepository.findById(dispatch.getStationId()).orElse(null);
        userEntity adminUser = (station != null && station.getUserId() != null) ? userRepository.findById(station.getUserId()).orElse(null) : null;
        if (adminUser == null && station != null && station.getStationId() != null) {
            adminUser = userRepository.findAll().stream()
                    .filter(u -> station.getStationId().equals(u.getStationId()) && u.getRoles() != null && u.getRoles().contains("STATION_MANAGER"))
                    .findFirst().orElse(null);
        }
        dispatchDto dto = new dispatchDto(dispatch.getSeverity(), dispatch.getDispatchId().toHexString(), dispatch.getForceType(), dispatch.getAssignedVehicle(), dispatch.getAssignedPersonnel(), dispatch.getStatus());
        if (adminUser != null && adminUser.getUsername() != null) {
            messagingTemplate.convertAndSendToUser(adminUser.getUsername(), "/update/mission", dto);
        }
        messagingTemplate.convertAndSend("/update/mission", dto);

        return ResponseEntity.ok("Dispatch marked as fake successfully.");
    }

    private double calculateDistance(GeoJsonPoint p1, GeoJsonPoint p2) {
        double lat1 = p1.getY();
        double lon1 = p1.getX();
        double lat2 = p2.getY();
        double lon2 = p2.getX();
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)+ Math.cos(Math.toRadians(lat1))* Math.cos(Math.toRadians(lat2))* Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371 * c;
    }

    @Override
    @Async
    public void findStation(ObjectId disasterId,Integer newTrucks, Integer newPersonnel) {
        disasterEntity disaster = disasterRepository.findByDisasterId(disasterId);

        if(disaster == null){
            return;
        }

        double []weights = new double[disaster.getForces().size()];
        switch (disaster.getForces().size()) {
            case 1:
                weights[0] = 1;break;
            case 2:
                weights[0] = 0.60;weights[1] = 0.40;break;
            case 3:
                weights[0] = 0.50;weights[1] = 0.30;weights[2] = 0.20;break;
            case 4:
                weights[0] = 0.40;weights[1] = 0.30;weights[2] = 0.20;weights[3] = 0.10;break;
            case 5:
                weights[0] = 0.30;weights[1] = 0.25;weights[2] = 0.20;weights[3] = 0.15;weights[4] = 0.10;break;
            case 6:
                weights[0] = 0.25;weights[1] = 0.20;weights[2] = 0.15;weights[3] = 0.15;weights[4] = 0.15;weights[5] = 0.10;break;
            default:
                break;
        }
        int index = 0;
        for(Forces force : disaster.getForces()){
            Integer requiredTrucks,requiredPersonnel;
            List<stationEntity> stations = stationRepository.findByStatusAndForceTypeAndStationIdNotInAndLocationNear(Station.ACTIVE,force,disaster.getRejectedStations(),disaster.getLocation());
            stations.sort((a, b) -> {
                double distanceA = calculateDistance(disaster.getLocation(),a.getLocation());
                double distanceB = calculateDistance(disaster.getLocation(),b.getLocation());
                return Double.compare(distanceA, distanceB);
            });
            if (stations.isEmpty()) {
                disasterDto dto = new disasterDto();
                dto.setDisasterId(disaster.getDisasterId().toHexString());
                dto.setStatus(disaster.getStatus());
                dto.setAiStatus(disaster.getAiStatus());
                dto.setSeverity(disaster.getSeverity());
                dto.setAiConfidence(disaster.getAiConfidence());
                dto.setFinalConfidence(disaster.getFinalConfidence());
                dto.setSuspicious(disaster.getSuspicious());
                if (disaster.getImage() != null) {
                    dto.setImage(disaster.getImage().toHexString());
                }
                dto.setAssignmentStatus(disaster.getAssignmentStatus());
                dto.setState(disaster.getState());
                dto.setUserReport(disaster.getUserReport());
                dto.setReportCount(disaster.getReportCount());
                dto.setDisasterType(disaster.getDisasterType());
                dto.setForces(Collections.singleton(force)); // this is the only force that failed to find a station so we set it in the dto by creating a singleton set
                userEntity adminUser = (disaster.getAssignedAdminId() != null)
                        ? userRepository.findById(disaster.getAssignedAdminId()).orElse(null)
                        : null;
                if (adminUser != null && adminUser.getUsername() != null) {
                    messagingTemplate.convertAndSendToUser(adminUser.getUsername(), "/topic/disaster", dto);
                }
                messagingTemplate.convertAndSend("/topic/disaster", dto);
                index++;
                continue;
            }
            stationEntity station = stations.get(0);
            if (index == disaster.getForces().size() - 1) {
                requiredTrucks = newTrucks;
                requiredPersonnel = newPersonnel;
            } 
            else {
                requiredTrucks =  (int) Math.ceil(newTrucks * weights[index]);
                requiredPersonnel = (int) Math.ceil(newPersonnel * weights[index]);
                requiredTrucks = Math.min(requiredTrucks, newTrucks);
                requiredPersonnel = Math.min(requiredPersonnel, newPersonnel);
            }
            if (station != null) {
                dispatchEntity dispatch = new dispatchEntity();
                dispatch.setDisasterId(disasterId);
                dispatch.setStationId(station.getStationId());
                dispatch.setAssignedVehicle(requiredTrucks);
                dispatch.setAssignedPersonnel(requiredPersonnel);
                dispatch.setForceType(force);
                dispatch.setSeverity(disaster.getSeverity());
                dispatch.setStatus(Status.VERIFIED);
                dispatchRepository.save(dispatch);
                dispatchDto dto = new dispatchDto();
                dto.setDispatchId(dispatch.getDispatchId().toHexString());
                dto.setSeverity(dispatch.getSeverity());
                dto.setForceType(dispatch.getForceType());
                dto.setAssignedVehicle(dispatch.getAssignedVehicle());
                dto.setAssignedPersonnel(dispatch.getAssignedPersonnel());
                dto.setStatus(dispatch.getStatus());
                userEntity stationManager = userRepository.findById(station.getUserId()).orElse(null);
                if (stationManager != null && stationManager.getUsername() != null) {
                    messagingTemplate.convertAndSendToUser(stationManager.getUsername(), "/update/mission", dto);
                }
                newTrucks -= requiredTrucks;
                newPersonnel -= requiredPersonnel;
            }
            index++;
        }
    }

    @Override
    public ResponseEntity<stationDashboard> getMissionsForStation(String username) {
        userEntity user = userRepository.findByUsername(username);
        List<dispatchEntity> dispatches = dispatchRepository.findByStationIdAndStatusIn(
                user.getStationId(),
                List.of(Status.VERIFIED, Status.DISPATCH_COMPLETED, Status.BACKUP_DISPATCH_COMPLETED, Status.DISPATCH_FALSE)
        );
        List<dispatchDto> dispatchDtos = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for(dispatchEntity dispatch : dispatches){
            dispatchDto dto = new dispatchDto();
            dto.setDispatchId(dispatch.getDispatchId().toHexString());
            dto.setSeverity(dispatch.getSeverity());
            dto.setForceType(dispatch.getForceType());
            dto.setAssignedVehicle(dispatch.getAssignedVehicle());
            dto.setAssignedPersonnel(dispatch.getAssignedPersonnel());
            dto.setStatus(dispatch.getStatus());
            dispatchDtos.add(dto);
            seenIds.add(dto.getDispatchId());
        }
        List<backupEntity> pendingBackups = backupRepo.findByStationIdAndStatus(user.getStationId(), backupStatus.PENDING);
        if (pendingBackups != null) {
            for (backupEntity b : pendingBackups) {
                if (b.getDispatchId() != null && !seenIds.contains(b.getDispatchId().toHexString())) {
                    dispatchDto dto = new dispatchDto();
                    dto.setDispatchId(b.getDispatchId().toHexString());
                    dto.setSeverity(b.getSeverity());
                    dto.setForceType(b.getForce());
                    dto.setAssignedVehicle(b.getReqVehicles());
                    dto.setAssignedPersonnel(b.getReqPersonnel());
                    dto.setStatus(Status.BACKUP_REQUEST);
                    dispatchDtos.add(dto);
                    seenIds.add(b.getDispatchId().toHexString());
                }
            }
        }
        Stats stats = user.getStats();
        return ResponseEntity.ok(new stationDashboard(stats,dispatchDtos));
    }

    @Override
    @Transactional
    public ResponseEntity<?> approveMisssion(ObjectId dispatchId,String username) {

        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElseThrow(() ->new RuntimeException("Dispatch not found"));

        backupEntity pendingBackup = backupRepo.findByDispatchId(dispatchId);
        boolean isBackupApproval = (dispatch.getStatus() == Status.BACKUP_REQUEST)
                || (pendingBackup != null && pendingBackup.getStatus() == backupStatus.PENDING);

        if(isBackupApproval){
            backupEntity backup = pendingBackup != null ? pendingBackup : backupRepo.findByDispatchId(dispatchId);
            if (backup == null) {
                return ResponseEntity.badRequest().body("Associated backup record not found.");
            }

            userEntity manager = userRepository.findByUsername(username);
            if (!dispatch.getStationId().equals(manager.getStationId())
                    && (backup.getStationId() == null || !backup.getStationId().equals(manager.getStationId()))) {
                return ResponseEntity.status(403).body("You are not authorized to approve this mission.");
            }

            resourceEntity resource = resourceRepo.findByStationId(manager.getStationId());
            disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());

            stationEntity station = stationRepository.findById(manager.getStationId()).orElseThrow(() ->new RuntimeException("Station not found"));

            if (disaster == null) {
                return ResponseEntity.badRequest().body("Disaster not found.");
            }

            if (resource == null) {
                return ResponseEntity.badRequest().body("Station resources not found.");
            }

            List<userEntity> workers = userRepository.findByStationIdAndWorkerStatus(station.getStationId(), Admin.AVAILABLE);
            if (workers.isEmpty()) {
                return ResponseEntity.badRequest().body("No available workers at this station.");
            }

            if (resource.getAvailablePersonnel() < backup.getReqPersonnel()) {
                return ResponseEntity.badRequest().body("Not enough available personnel.");
            }

            if (resource.getAvailableVehicle() < backup.getReqVehicles()) {
                return ResponseEntity.badRequest().body("Not enough available vehicles.");
            }

            resource.setAvailablePersonnel(Math.max(0, resource.getAvailablePersonnel() - backup.getReqPersonnel()));
            resource.setAvailableVehicle(Math.max(0, resource.getAvailableVehicle() - backup.getReqVehicles()));
            resourceRepo.save(resource);

            userEntity worker = workers.get(0);
            worker.setWorkerStatus(Admin.BUSY);
            userRepository.save(worker);

            // Restore/keep original dispatch active so original worker and simulation continue
            dispatch.setStatus(Status.DISPATCHED);
            dispatchRepository.save(dispatch);

            // Create new independent backup dispatch for the assigned reinforcement unit
            dispatchEntity backupDispatch = new dispatchEntity();
            backupDispatch.setDisasterId(disaster.getDisasterId());
            backupDispatch.setStationId(station.getStationId());
            backupDispatch.setForceType(station.getForceType() != null ? station.getForceType() : backup.getForce());
            backupDispatch.setSeverity(disaster.getSeverity());
            backupDispatch.setAssignedVehicle(backup.getReqVehicles());
            backupDispatch.setAssignedPersonnel(backup.getReqPersonnel());
            backupDispatch.setStatus(Status.BACKUP_ASSIGNED);
            backupDispatch.setWorkerId(worker.getUserId());
            backupDispatch.setDispatchedAt(LocalDateTime.now());
            dispatchRepository.save(backupDispatch);

            disaster.setStatus(Status.BACKUP_ASSIGNED);
            if (!disaster.getStationId().contains(station.getStationId())) {
                disaster.getStationId().add(station.getStationId());
            }
            disasterRepository.save(disaster);

            backup.setStatus(backupStatus.ASSIGNED);
            backup.setStationId(station.getStationId());
            backup.setAssingnedAt(LocalDateTime.now());
            backup.setWorkerId(worker.getUserId());
            backup.setDispatchId(backupDispatch.getDispatchId());
            backupRepo.save(backup);

            messagingTemplate.convertAndSendToUser(worker.getUsername(), "/new/mission", new dispatchDto(backupDispatch.getSeverity(),backupDispatch.getDispatchId().toHexString(),backupDispatch.getForceType(),backup.getReqVehicles(),backup.getReqPersonnel(),backupDispatch.getStatus()));
            userEntity adminUser = userRepository.findById(disaster.getAssignedAdminId()).orElse(null);
            if (adminUser != null && adminUser.getUsername() != null) {
                messagingTemplate.convertAndSendToUser(adminUser.getUsername(), "/topic/disaster", new reportResponse(disaster.getDisasterId().toHexString(),"Backup Dispatch approved.",disaster.getStatus()));
            }
            return ResponseEntity.ok("Backup Dispatch approved successfully.");
        }

        if (!dispatch.getStationId().equals(userRepository.findByUsername(username).getStationId())) {
            return ResponseEntity.status(403).body("You are not authorized to approve this mission.");
        }

        if (dispatch.getStatus() == Status.DISPATCHED) {
            return ResponseEntity.badRequest().body("This mission has already been processed.");
        }

        resourceEntity resource = resourceRepo.findByStationId(dispatch.getStationId());
        disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());

        stationEntity station = stationRepository.findById(dispatch.getStationId()).orElseThrow(() ->new RuntimeException("Station not found"));

        if (disaster == null) {
            return ResponseEntity.badRequest().body("Disaster not found.");
        }

        if (resource == null) {
            return ResponseEntity.badRequest().body("Station resources not found.");
        }

        List<userEntity> workers = userRepository.findByStationIdAndWorkerStatus(station.getStationId(), Admin.AVAILABLE);
        if (workers.isEmpty()) {
            return ResponseEntity.badRequest().body("No available workers at this station.");
        }

        if (resource.getAvailablePersonnel() < dispatch.getAssignedPersonnel()) {
            return ResponseEntity.badRequest().body("Not enough available personnel.");
        }

        if (resource.getAvailableVehicle() < dispatch.getAssignedVehicle()) {
            return ResponseEntity.badRequest().body("Not enough available vehicles.");
        }

        resource.setAvailablePersonnel(Math.max(0, resource.getAvailablePersonnel() - dispatch.getAssignedPersonnel()));
        resource.setAvailableVehicle(Math.max(0, resource.getAvailableVehicle() - dispatch.getAssignedVehicle()));
        resourceRepo.save(resource);

        userEntity worker = workers.get(0);
        worker.setWorkerStatus(Admin.BUSY);
        userRepository.save(worker);

        dispatch.setStatus(Status.DISPATCH_ASSIGNED);
        dispatch.setWorkerId(worker.getUserId());
        dispatch.setDispatchedAt(LocalDateTime.now());
        dispatchRepository.save(dispatch);

        disaster.setStatus(Status.DISPATCH_ASSIGNED);
        disaster.setDispatchedAt(dispatch.getDispatchedAt());
        disaster.getStationId().add(station.getStationId());
        disasterRepository.save(disaster);

        messagingTemplate.convertAndSendToUser(worker.getUsername(), "/new/mission", new dispatchDto(dispatch.getSeverity(),dispatch.getDispatchId().toHexString(),dispatch.getForceType(),dispatch.getAssignedVehicle(),dispatch.getAssignedPersonnel(),dispatch.getStatus()));
        userEntity adminUser = userRepository.findById(disaster.getAssignedAdminId()).orElse(null);
        if (adminUser != null && adminUser.getUsername() != null) {
            messagingTemplate.convertAndSendToUser(adminUser.getUsername(), "/topic/disaster", new reportResponse(disaster.getDisasterId().toHexString(),"Dispatch approved.",disaster.getStatus()));
        }
        reportResponse dispatchApprovedResponse = new reportResponse(disaster.getDisasterId().toHexString(), "Dispatch approved.", disaster.getStatus());
        userEntity disasterReporter = userRepository.findByUserId(disaster.getUserId());
        if (disasterReporter != null && disasterReporter.getUsername() != null) {
            messagingTemplate.convertAndSendToUser(disasterReporter.getUsername(), "/queue/report", dispatchApprovedResponse);
        }
        return ResponseEntity.ok("Dispatch approved successfully.");
    } 

    @Override
    @Async
    public void newMission(ObjectId dispatchId, Forces force){
        
        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElseThrow(() -> new RuntimeException("Dispatch not found"));

        backupEntity pendingBackup = backupRepo.findByDispatchId(dispatchId);
        boolean isBackupReassignment = (dispatch.getStatus() == Status.BACKUP_REQUEST)
                || (pendingBackup != null && pendingBackup.getStatus() == backupStatus.PENDING);

        if(isBackupReassignment){

            backupEntity backup = pendingBackup != null ? pendingBackup : backupRepo.findByDispatchId(dispatchId);
            disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());

            if(disaster == null){
                return;
            }
        
            List<stationEntity> stations = stationRepository.findByStatusAndForceTypeAndStationIdNotInAndLocationNear(Station.ACTIVE,force,disaster.getRejectedStations(),disaster.getLocation());
            stations.sort((a, b) -> {
                double distanceA = calculateDistance(disaster.getLocation(),a.getLocation());
                double distanceB = calculateDistance(disaster.getLocation(),b.getLocation());
                return Double.compare(distanceA, distanceB);
            });

            if (stations.isEmpty()) {

                disaster.setStatus(Status.BACKUP_FAILED);
                disasterRepository.save(disaster);

                if (backup != null) {
                    backup.setStatus(backupStatus.FAILED);
                    backupRepo.save(backup);
                }

                // Keep original dispatch active so its mission continues
                dispatch.setStatus(Status.DISPATCHED);
                dispatchRepository.save(dispatch);
                
                disasterDto dto = new disasterDto();
                dto.setDisasterId(disaster.getDisasterId().toHexString());
                dto.setStatus(disaster.getStatus());
                dto.setAiStatus(disaster.getAiStatus());
                dto.setSeverity(disaster.getSeverity());
                dto.setAiConfidence(disaster.getAiConfidence());
                dto.setFinalConfidence(disaster.getFinalConfidence());
                dto.setSuspicious(disaster.getSuspicious());
                dto.setImage(disaster.getImage().toHexString());
                dto.setAssignmentStatus(disaster.getAssignmentStatus());
                dto.setState(disaster.getState());
                dto.setUserReport(disaster.getUserReport());
                dto.setReportCount(disaster.getReportCount());
                dto.setDisasterType(disaster.getDisasterType());
                dto.setForces(Collections.singleton(force)); // this is the only force that failed to find a station so we set it in the dto by creating a singleton set
                userEntity adminUser = userRepository.findById(disaster.getAssignedAdminId()).orElse(null);
                if (adminUser != null && adminUser.getUsername() != null) {
                    messagingTemplate.convertAndSendToUser(adminUser.getUsername(), "/topic/disaster", dto);
                }
                return;
            }

            stationEntity station = stations.get(0);

            // Keep original dispatch active so its mission continues
            dispatch.setStatus(Status.DISPATCHED);
            dispatchRepository.save(dispatch);

            disaster.setStatus(Status.BACKUP_REQUEST);
            disasterRepository.save(disaster);

            if (backup != null) {
                backup.setStationId(station.getStationId());
                backupRepo.save(backup);
            }

            dispatchDto dto = new dispatchDto();
            dto.setDispatchId(dispatch.getDispatchId().toHexString());
            dto.setSeverity(dispatch.getSeverity());
            dto.setForceType(dispatch.getForceType());
            dto.setAssignedVehicle(backup != null ? backup.getReqVehicles() : 1);
            dto.setAssignedPersonnel(backup != null ? backup.getReqPersonnel() : 4);
            dto.setStatus(Status.BACKUP_REQUEST);
            userEntity stationManager = (station.getUserId() != null) ? userRepository.findById(station.getUserId()).orElse(null) : null;
            if (stationManager == null && station.getStationId() != null) {
                stationManager = userRepository.findAll().stream()
                        .filter(u -> station.getStationId().equals(u.getStationId())
                                && u.getRoles() != null && u.getRoles().contains("STATION_MANAGER"))
                        .findFirst().orElse(null);
            }
            if (stationManager != null && stationManager.getUsername() != null) {
                messagingTemplate.convertAndSendToUser(stationManager.getUsername(), "/update/mission", dto);
            }
            messagingTemplate.convertAndSend("/update/mission", dto);
            return;
        }

        disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());

        if(disaster == null){
            return;
        }
        
        List<stationEntity> stations = stationRepository.findByStatusAndForceTypeAndStationIdNotInAndLocationNear(Station.ACTIVE,force,disaster.getRejectedStations(),disaster.getLocation());
        stations.sort((a, b) -> {
            double distanceA = calculateDistance(disaster.getLocation(),a.getLocation());
            double distanceB = calculateDistance(disaster.getLocation(),b.getLocation());
            return Double.compare(distanceA, distanceB);
        });

        if (stations.isEmpty()) {
            disaster.setStatus(Status.DISPATCH_FAILED);
            disasterRepository.save(disaster);
            disasterDto dto = new disasterDto();
            dto.setDisasterId(disaster.getDisasterId().toHexString());
            dto.setStatus(disaster.getStatus());
            dto.setAiStatus(disaster.getAiStatus());
            dto.setSeverity(disaster.getSeverity());
            dto.setAiConfidence(disaster.getAiConfidence());
            dto.setFinalConfidence(disaster.getFinalConfidence());
            dto.setSuspicious(disaster.getSuspicious());
            dto.setImage(disaster.getImage().toHexString());
            dto.setAssignmentStatus(disaster.getAssignmentStatus());
            dto.setState(disaster.getState());
            dto.setUserReport(disaster.getUserReport());
            dto.setReportCount(disaster.getReportCount());
            dto.setDisasterType(disaster.getDisasterType());
            dto.setForces(Collections.singleton(force)); // this is the only force that failed to find a station so we set it in the dto by creating a singleton set
            userEntity adminUser = userRepository.findById(disaster.getAssignedAdminId()).orElse(null);
            if (adminUser != null && adminUser.getUsername() != null) {
                messagingTemplate.convertAndSendToUser(adminUser.getUsername(), "/topic/disaster", dto);
            }
            return;
        }

        stationEntity station = stations.get(0);

        dispatch.setStationId(station.getStationId());
        dispatchRepository.save(dispatch);

        dispatchDto dto = new dispatchDto();
        dto.setDispatchId(dispatch.getDispatchId().toHexString());
        dto.setSeverity(dispatch.getSeverity());
        dto.setForceType(dispatch.getForceType());
        dto.setAssignedVehicle(dispatch.getAssignedVehicle());
        dto.setAssignedPersonnel(dispatch.getAssignedPersonnel());
        dto.setStatus(dispatch.getStatus());
        userEntity stationManager = userRepository.findById(station.getUserId()).orElse(null);
        if (stationManager != null && stationManager.getUsername() != null) {
            messagingTemplate.convertAndSendToUser(stationManager.getUsername(), "/update/mission", dto);
        }        
    }

    @Override 
    @Transactional
    public ResponseEntity<?> completeMission(ObjectId dispatchId, String username) {

        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElseThrow(() -> new RuntimeException("Dispatch not found"));
        userEntity manager = userRepository.findByUsername(username);
        stationEntity station = (manager.getUserId() != null) ? stationRepository.findByUserId(manager.getUserId()) : null;
        if (station == null && manager.getStationId() != null) {
            station = stationRepository.findById(manager.getStationId()).orElse(null);
        }
        if (station == null) {
            return ResponseEntity.badRequest().body("Station not found.");
        }
        resourceEntity resource = resourceRepo.findByStationId(station.getStationId());

        if (!dispatch.getStationId().equals(manager.getStationId())) {
            return ResponseEntity.status(403).body("You are not authorized to complete this mission.");
        }

        if(dispatch.getStatus() == Status.BACKUP_DISPATCH_COMPLETED){

            backupEntity backup = backupRepo.findByDispatchId(dispatchId);            

            int reqP = backup != null && backup.getReqPersonnel() != null ? backup.getReqPersonnel() : (dispatch.getAssignedPersonnel() != null ? dispatch.getAssignedPersonnel() : 0);
            int reqV = backup != null && backup.getReqVehicles() != null ? backup.getReqVehicles() : (dispatch.getAssignedVehicle() != null ? dispatch.getAssignedVehicle() : 0);

            if (resource != null) {
                resource.setAvailablePersonnel(resource.getAvailablePersonnel() + reqP);
                resource.setAvailableVehicle(resource.getAvailableVehicle() + reqV);
                resourceRepo.save(resource);
            }

            if (backup != null) {
                backup.setStatus(backupStatus.COMPLETED);
                backupRepo.save(backup);
            }

            dispatch.setStatus(Status.COMPLETED);
            dispatch.setCompletedAt(LocalDateTime.now());
            dispatchRepository.save(dispatch);

            disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());
            if(disaster == null){
                return ResponseEntity.badRequest().body("Disaster not found.");
            }

            ObjectId workerId = (backup != null && backup.getWorkerId() != null) ? backup.getWorkerId() : dispatch.getWorkerId();
            if (workerId != null) {
                userEntity workerr = userRepository.findById(workerId).orElse(null);
                if (workerr != null) {
                    workerr.setWorkerStatus(Admin.AVAILABLE);
                    userRepository.save(workerr);
                }
            }

            Stats stats = manager.getStats();
            if (stats == null) { stats = new Stats(0, 0, 0, 0); }
            stats.setCompleted(stats.getCompleted() + 1);
            stats.setTotalAssigned(stats.getTotalAssigned() + 1);
            manager.setStats(stats);
            userRepository.save(manager);

            completeDisasterBackup(disaster.getDisasterId());

            return ResponseEntity.ok("Backup Dispatch completed successfully.");
        }

        if (dispatch.getStatus() != Status.DISPATCH_COMPLETED) {
            return ResponseEntity.badRequest().body("This mission is not in a state that can be completed.");
        }

        if (resource != null) {
            resource.setAvailablePersonnel(resource.getAvailablePersonnel() + (dispatch.getAssignedPersonnel() != null ? dispatch.getAssignedPersonnel() : 0));
            resource.setAvailableVehicle(resource.getAvailableVehicle() + (dispatch.getAssignedVehicle() != null ? dispatch.getAssignedVehicle() : 0));
            resourceRepo.save(resource);
        }

        dispatch.setStatus(Status.COMPLETED);
        dispatch.setCompletedAt(LocalDateTime.now());
        dispatchRepository.save(dispatch);

        if (dispatch.getWorkerId() != null) {
            userEntity workerr = userRepository.findById(dispatch.getWorkerId()).orElse(null);
            if (workerr != null) {
                workerr.setWorkerStatus(Admin.AVAILABLE);
                userRepository.save(workerr);
            }
        }

        Stats stats = manager.getStats();
        if (stats == null) { stats = new Stats(0, 0, 0, 0); }
        stats.setCompleted(stats.getCompleted() + 1);
        stats.setTotalAssigned(stats.getTotalAssigned() + 1);
        manager.setStats(stats);
        userRepository.save(manager);
        
        disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());
        if(disaster == null){
            return ResponseEntity.badRequest().body("Disaster not found.");
        }

        completeDisasterNormal(disaster.getDisasterId());

        return ResponseEntity.ok("Dispatch completed successfully.");
    }

    @Override
    @Transactional 
    public ResponseEntity<?> fakeMission(ObjectId dispatchId, String username) {
        
        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElseThrow(() -> new RuntimeException("Dispatch not found"));
        userEntity manager = userRepository.findByUsername(username);
        stationEntity station = (manager.getUserId() != null) ? stationRepository.findByUserId(manager.getUserId()) : null;
        if (station == null && manager.getStationId() != null) {
            station = stationRepository.findById(manager.getStationId()).orElse(null);
        }
        if (station == null) {
            return ResponseEntity.badRequest().body("Station not found.");
        }
        resourceEntity resource = resourceRepo.findByStationId(station.getStationId());

        if (!dispatch.getStationId().equals(manager.getStationId())){
            return ResponseEntity.status(403).body("You are not authorized to mark this mission as fake.");
        }
        if (dispatch.getStatus() != Status.DISPATCH_FALSE) {
            return ResponseEntity.badRequest().body("This mission is not in a state that can be marked as fake.");
        }

        backupEntity backup = backupRepo.findByDispatchId(dispatchId);
        if (backup != null) {
            backup.setStatus(backupStatus.FAILED);
            backupRepo.save(backup);
        }

        int addP = dispatch.getAssignedPersonnel() != null ? dispatch.getAssignedPersonnel() : (backup != null && backup.getReqPersonnel() != null ? backup.getReqPersonnel() : 0);
        int addV = dispatch.getAssignedVehicle() != null ? dispatch.getAssignedVehicle() : (backup != null && backup.getReqVehicles() != null ? backup.getReqVehicles() : 0);

        if (resource != null) {
            resource.setAvailablePersonnel(resource.getAvailablePersonnel() + addP);
            resource.setAvailableVehicle(resource.getAvailableVehicle() + addV);
            resourceRepo.save(resource);
        }

        dispatch.setStatus(Status.DISPATCH_CANCELLED);
        dispatch.setCompletedAt(LocalDateTime.now());
        dispatchRepository.save(dispatch);

        disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());
        if(disaster != null){
            completeDisasterReject(disaster.getDisasterId());
        }

        ObjectId workerId = dispatch.getWorkerId() != null ? dispatch.getWorkerId() : (backup != null ? backup.getWorkerId() : null);
        if (workerId != null) {
            userEntity workerr = userRepository.findById(workerId).orElse(null);
            if (workerr != null) {
                workerr.setWorkerStatus(Admin.AVAILABLE);
                userRepository.save(workerr);
            }
        }

        Stats stats = manager.getStats();
        if (stats == null) { stats = new Stats(0, 0, 0, 0); }
        stats.setMarkedFalse(stats.getMarkedFalse() + 1);
        stats.setTotalAssigned(stats.getTotalAssigned() + 1);
        manager.setStats(stats);
        userRepository.save(manager);        
                
        return ResponseEntity.ok("Dispatch marked as fake successfully.");
    }

    @Override 
    @Transactional
    public ResponseEntity<?> requestBackup(String username, backupDTO dto, ObjectId dispatchId) {

        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElseThrow(() -> new RuntimeException("dispatch not found"));
        userEntity user = userRepository.findByUsername(username);

        if (dispatch.getStatus() != Status.DISPATCHED){
            return ResponseEntity.status(403).body("Dispatch is yet to take place.");
        }

        if(!user.getRoles().contains("WORKER")){
            return ResponseEntity.status(403).body("Only workers can request backup.");
        }

        if (!dispatch.getStationId().equals(user.getStationId())) {
            return ResponseEntity.status(403).body("You are not authorized to request backup for this dispatch.");
        }

        if (!hasDispatchArrived(dispatch)) {
            return ResponseEntity.badRequest().body("Unit has not yet arrived at incident destination.");
        }

        stationEntity station = stationRepository.findById(user.getStationId()).orElseThrow(() -> new RuntimeException("Station not found"));
        disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());

        if (disaster == null) {
            return ResponseEntity.badRequest().body("No active disaster found for this station.");
        }

        backupEntity backup = new backupEntity();
        backup.setDispatchId(dispatchId);
        backup.setSeverity(disaster.getSeverity());
        backup.setDisasterId(disaster.getDisasterId());
        backup.setForce(station.getForceType());
        backup.setReqVehicles(dto.getReqVehicles());
        backup.setReqPersonnel(dto.getReqPersonnel());
        backup.setStatus(backupStatus.PENDING);
        backup.setRequestedAt(LocalDateTime.now());
        backup.setStationId(station.getStationId());
        backupRepo.save(backup);

        dispatch.setStatus(Status.BACKUP_REQUEST);
        dispatchRepository.save(dispatch);

        disaster.setStatus(Status.BACKUP_REQUEST);
        disasterRepository.save(disaster);
        
        dispatchDto dtoo = new dispatchDto();
        dtoo.setDispatchId(dispatchId.toHexString());
        dtoo.setSeverity(dispatch.getSeverity());
        dtoo.setForceType(backup.getForce());
        dtoo.setAssignedVehicle(dto.getReqVehicles());
        dtoo.setAssignedPersonnel(dto.getReqPersonnel());
        dtoo.setStatus(dispatch.getStatus());

        userEntity stationManager = (station.getUserId() != null)
                ? userRepository.findById(station.getUserId()).orElse(null)
                : null;
        if (stationManager == null && station.getStationId() != null) {
            stationManager = userRepository.findAll().stream()
                    .filter(u -> station.getStationId().equals(u.getStationId())
                            && u.getRoles() != null && u.getRoles().contains("STATION_MANAGER"))
                    .findFirst().orElse(null);
        }
        if (stationManager != null && stationManager.getUsername() != null) {
            messagingTemplate.convertAndSendToUser(stationManager.getUsername(), "/update/mission", dtoo);
        }
        messagingTemplate.convertAndSend("/update/mission", dtoo);
        return ResponseEntity.ok("Backup request sent successfully.");
    }

    @Override
    public ResponseEntity<dispatches> getAssignedDispatches(String username) {
        
        userEntity worker = userRepository.findByUsername(username);
        List<dispatchEntity> dispatchEntities = dispatchRepository.findByWorkerIdAndStatusIn(
                worker.getUserId(),
                List.of(Status.DISPATCH_ASSIGNED, Status.DISPATCHED, Status.BACKUP_REQUEST, Status.BACKUP_ASSIGNED, Status.BACKUP_DISPATCHED));
        List<backupEntity> backupEntities = backupRepo.findByWorkerIdAndStatusIn(
                worker.getUserId(),
                List.of(backupStatus.ASSIGNED, backupStatus.DISPATCHED));
        List<dispatchDto> dispatchDtos = new ArrayList<>();
        for (dispatchEntity dispatch : dispatchEntities) {
            dispatchDto dto = new dispatchDto();
            dto.setDispatchId(dispatch.getDispatchId().toHexString());
            dto.setSeverity(dispatch.getSeverity());
            dto.setForceType(dispatch.getForceType());
            dto.setAssignedVehicle(dispatch.getAssignedVehicle());
            dto.setAssignedPersonnel(dispatch.getAssignedPersonnel());
            dto.setStatus(dispatch.getStatus());
            dto.setArrived(hasDispatchArrived(dispatch));
            dispatchDtos.add(dto);
        }
        List<dtoBackup> backupDtos = new ArrayList<>();
        for (backupEntity backup : backupEntities) {
            dtoBackup dto = new dtoBackup();
            dto.setBackupId(backup.getBackupId().toHexString());
            dto.setSeverity(backup.getSeverity());
            dto.setForceType(backup.getForce());
            dto.setAssignedVehicle(backup.getReqVehicles());
            dto.setAssignedPersonnel(backup.getReqPersonnel());
            dto.setStatus(backup.getStatus());
            backupDtos.add(dto);
        }
        return ResponseEntity.ok(new dispatches(dispatchDtos, backupDtos));
    }

    private static final java.util.Set<Status> ACTIVE_DISPATCH_STATUSES = java.util.Set.of(
            Status.VERIFIED,
            Status.DISPATCH_ASSIGNED,
            Status.DISPATCHED,
            Status.DISPATCH_COMPLETED,
            Status.BACKUP_REQUEST,
            Status.BACKUP_ASSIGNED,
            Status.BACKUP_DISPATCHED,
            Status.BACKUP_DISPATCH_COMPLETED
    );

    private static final java.util.Set<backupStatus> ACTIVE_BACKUP_STATUSES = java.util.Set.of(
            backupStatus.PENDING,
            backupStatus.ASSIGNED,
            backupStatus.DISPATCHED,
            backupStatus.BACKUP_DISPATCH_COMPLETED
    );

    @Override
    public void completeDisasterNormal(ObjectId disasterId){

        List<dispatchEntity> dispatches = dispatchRepository.findByDisasterId(disasterId);
        List<backupEntity> backups = backupRepo.findByDisasterId(disasterId);

        boolean hasActiveDispatches = dispatches.stream()
                .anyMatch(d -> d.getStatus() != null && ACTIVE_DISPATCH_STATUSES.contains(d.getStatus()));
        boolean hasActiveBackups = backups.stream()
                .anyMatch(b -> b.getStatus() != null && ACTIVE_BACKUP_STATUSES.contains(b.getStatus()));

        if(!hasActiveDispatches && !hasActiveBackups){
            
            disasterEntity disaster = disasterRepository.findByDisasterId(disasterId);
            if(disaster == null || disaster.getStatus() == Status.COMPLETED){
                return;
            }
            
            disaster.setStatus(Status.COMPLETED);
            disaster.setCompletedAt(LocalDateTime.now());
            disasterRepository.save(disaster);

            userEntity admin = null;
            if(disaster.getAssignedAdminId() != null){
                admin = userRepository.findById(disaster.getAssignedAdminId()).orElse(null);
                if(admin != null){
                    admin.setActiveIncidents(Math.max(0, admin.getActiveIncidents() - 1));
                    Stats stat = admin.getStats();
                    if(stat != null){
                        stat.setCompleted(stat.getCompleted() + 1);
                        stat.setTotalAssigned(stat.getTotalAssigned() + 1);
                        admin.setStats(stat);
                    }
                    userRepository.save(admin);
                }
            }

            if(disaster.getUserId() != null){
                userEntity user = userRepository.findById(disaster.getUserId()).orElse(null);
                if(user != null){
                    user.setTrustScore(user.getTrustScore() + 10);
                    userRepository.save(user);

                    reportResponse normalCompResponse = new reportResponse(disaster.getDisasterId().toHexString(), "Mission completed.", disaster.getStatus());
                    if(admin != null && admin.getUsername() != null){
                        messagingTemplate.convertAndSendToUser(admin.getUsername(), "/topic/disaster", normalCompResponse);
                    }
                    messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/report", normalCompResponse);
                }
            }

        }

    }

    @Override
    public void completeDisasterBackup(ObjectId disasterId){

        List<dispatchEntity> dispatches = dispatchRepository.findByDisasterId(disasterId);
        List<backupEntity> backups = backupRepo.findByDisasterId(disasterId);

        boolean hasActiveDispatches = dispatches.stream()
                .anyMatch(d -> d.getStatus() != null && ACTIVE_DISPATCH_STATUSES.contains(d.getStatus()));
        boolean hasActiveBackups = backups.stream()
                .anyMatch(b -> b.getStatus() != null && ACTIVE_BACKUP_STATUSES.contains(b.getStatus()));

        if(!hasActiveDispatches && !hasActiveBackups){
            
            disasterEntity disaster = disasterRepository.findByDisasterId(disasterId);
            if(disaster == null || disaster.getStatus() == Status.COMPLETED){
                return;
            }
            
            disaster.setStatus(Status.COMPLETED);
            disaster.setCompletedAt(LocalDateTime.now());
            disasterRepository.save(disaster);

            userEntity admin = null;
            if(disaster.getAssignedAdminId() != null){
                admin = userRepository.findById(disaster.getAssignedAdminId()).orElse(null);
                if(admin != null){
                    admin.setActiveIncidents(Math.max(0, admin.getActiveIncidents() - 1));
                    Stats stat = admin.getStats();
                    if(stat != null){
                        stat.setCompleted(stat.getCompleted() + 1);
                        stat.setTotalAssigned(stat.getTotalAssigned() + 1);
                        admin.setStats(stat);
                    }
                    userRepository.save(admin);
                }
            }

            if(disaster.getUserId() != null){
                userEntity user = userRepository.findById(disaster.getUserId()).orElse(null);
                if(user != null){
                    user.setTrustScore(user.getTrustScore() + 10);
                    userRepository.save(user);

                    reportResponse backupCompResponse = new reportResponse(disaster.getDisasterId().toHexString(), "Backup Mission completed.", disaster.getStatus());
                    reportResponse userMissionCompResponse = new reportResponse(disaster.getDisasterId().toHexString(), "Mission completed.", disaster.getStatus());
                    if(admin != null && admin.getUsername() != null){
                        messagingTemplate.convertAndSendToUser(admin.getUsername(), "/topic/disaster", backupCompResponse);
                    }
                    messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/report", userMissionCompResponse);
                }
            }

        }
        
    }

    @Override 
    public void completeDisasterReject(ObjectId disasterId){

        List<dispatchEntity> dispatches = dispatchRepository.findByDisasterId(disasterId);

        boolean hasActiveDispatches = dispatches.stream()
                .anyMatch(d -> d.getStatus() != null && ACTIVE_DISPATCH_STATUSES.contains(d.getStatus()));

        if(!hasActiveDispatches){
            
            disasterEntity disaster = disasterRepository.findByDisasterId(disasterId);
            if(disaster == null || disaster.getStatus() == Status.DISPATCH_CANCELLED){
                return;
            }
            
            disaster.setStatus(Status.DISPATCH_CANCELLED);
            disaster.setCompletedAt(LocalDateTime.now());
            disasterRepository.save(disaster);

            userEntity admin = null;
            if(disaster.getAssignedAdminId() != null){
                admin = userRepository.findById(disaster.getAssignedAdminId()).orElse(null);
                if(admin != null){
                    admin.setActiveIncidents(Math.max(0, admin.getActiveIncidents() - 1));
                    Stats stat = admin.getStats();
                    if(stat != null){
                        stat.setMarkedFalse(stat.getMarkedFalse() + 1);
                        stat.setTotalAssigned(stat.getTotalAssigned() + 1);
                        admin.setStats(stat);
                    }
                    userRepository.save(admin);
                }
            }

            if(disaster.getUserId() != null){
                userEntity user = userRepository.findById(disaster.getUserId()).orElse(null);
                if(user != null){
                    user.setTrustScore(Math.max(0, user.getTrustScore() - 20));
                    userRepository.save(user);

                    reportResponse adminFakeResponse = new reportResponse(disaster.getDisasterId().toHexString(), "Mission marked as fake.", disaster.getStatus());
                    reportResponse userFakeResponse = new reportResponse(disaster.getDisasterId().toHexString(), "Mission marked as fake. Please do not spam this platform.", disaster.getStatus());
                    if(admin != null && admin.getUsername() != null){
                        messagingTemplate.convertAndSendToUser(admin.getUsername(), "/topic/disaster", adminFakeResponse);
                    }
                    messagingTemplate.convertAndSendToUser(user.getUsername(), "/queue/report", userFakeResponse);
                }
            }

        }
    }

    @Autowired
    private recallRequestRepo recallRequestRepository;

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    // ─────────────────────────────── RECALL WORKFLOW ────────────────────────────

    @Override
    public ResponseEntity<?> requestRecall(String username, ObjectId dispatchId, String reason) {

        // 1. Validate worker
        userEntity worker = userRepository.findByUsername(username);
        if (worker == null) {
            return ResponseEntity.status(404).body("Worker not found.");
        }
        if (worker.getRoles() == null || !worker.getRoles().contains("WORKER")) {
            return ResponseEntity.status(403).body("Only workers can request a recall.");
        }

        // 2. Load dispatch
        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElse(null);
        if (dispatch == null) {
            return ResponseEntity.status(404).body("Dispatch not found.");
        }

        // 3. Verify worker belongs to the dispatch's station
        if (!dispatch.getStationId().equals(worker.getStationId())) {
            return ResponseEntity.status(403).body("You are not authorized to recall this dispatch.");
        }

        // 4. Only dispatches that are actively moving may be recalled
        if (dispatch.getStatus() != Status.DISPATCHED && dispatch.getStatus() != Status.BACKUP_DISPATCHED) {
            return ResponseEntity.badRequest().body("Recall is only allowed for dispatches that are currently DISPATCHED or BACKUP_DISPATCHED.");
        }

        if (!hasDispatchArrived(dispatch)) {
            return ResponseEntity.badRequest().body("Unit has not yet arrived at incident destination.");
        }

        // 5. Prevent duplicate pending recall requests for the same dispatch
        if (recallRequestRepository.findByDispatchIdAndStatus(dispatchId, recallStatus.PENDING).isPresent()) {
            return ResponseEntity.status(409).body("A recall request for this dispatch is already pending.");
        }

        // 6. Find station manager for this station
        stationEntity station = stationRepository.findById(dispatch.getStationId()).orElse(null);
        if (station == null) {
            return ResponseEntity.status(404).body("Station not found.");
        }
        userEntity manager = (station.getUserId() != null)
                ? userRepository.findById(station.getUserId()).orElse(null)
                : null;

        // 7. Persist the recall request BEFORE sending the WebSocket notification
        recallRequestEntity recall = new recallRequestEntity();
        recall.setRecallId(new ObjectId());
        recall.setRequestedAt(LocalDateTime.now());
        recall.setDispatchId(dispatchId);
        recall.setDisasterId(dispatch.getDisasterId());
        recall.setStationId(dispatch.getStationId());
        recall.setRequestingWorkerId(worker.getUserId());
        recall.setRequestingWorkerUsername(username);
        recall.setForceType(dispatch.getForceType());
        recall.setReason(reason != null ? reason : "");
        recall.setStatus(recallStatus.PENDING);
        recallRequestRepository.save(recall);

        // 8. Notify the station manager via WebSocket
        if (manager != null && manager.getUsername() != null) {
            recallRequestDto dto = new recallRequestDto(
                    recall.getRecallId().toHexString(),
                    dispatchId.toHexString(),
                    dispatch.getDisasterId() != null ? dispatch.getDisasterId().toHexString() : null,
                    station.getStationId().toHexString(),
                    station.getStationName(),
                    dispatch.getForceType(),
                    username,
                    recall.getReason(),
                    recall.getRequestedAt(),
                    recallStatus.PENDING,
                    dispatch.getStatus()
            );
            messagingTemplate.convertAndSendToUser(manager.getUsername(), "/update/mission", dto);
        }

        return ResponseEntity.ok("Recall request submitted. Recall ID: " + recall.getRecallId().toHexString());
    }

    @Override
    @Transactional
    public ResponseEntity<?> approveRecall(String username, ObjectId recallId) {

        // 1. Load the recall request
        recallRequestEntity recall = recallRequestRepository.findById(recallId).orElse(null);
        if (recall == null) {
            return ResponseEntity.status(404).body("Recall request not found.");
        }

        // 2. Only PENDING requests can be approved
        if (recall.getStatus() != recallStatus.PENDING) {
            return ResponseEntity.badRequest().body("This recall request has already been " + recall.getStatus().name().toLowerCase() + ".");
        }

        // 3. Validate that the approving manager owns this station
        userEntity manager = userRepository.findByUsername(username);
        if (manager == null) {
            return ResponseEntity.status(404).body("Manager not found.");
        }
        stationEntity station = stationRepository.findById(recall.getStationId()).orElse(null);
        if (station == null) {
            return ResponseEntity.status(404).body("Station not found.");
        }
        if (!station.getUserId().equals(manager.getUserId())) {
            return ResponseEntity.status(403).body("You are not authorized to approve this recall request.");
        }

        // 4. Load the dispatch and guard against terminal states
        dispatchEntity dispatch = dispatchRepository.findById(recall.getDispatchId()).orElse(null);
        if (dispatch == null) {
            return ResponseEntity.status(404).body("Associated dispatch not found.");
        }
        if (dispatch.getStatus() == Status.RECALLED) {
            return ResponseEntity.badRequest().body("This dispatch has already been recalled.");
        }
        if (dispatch.getStatus() != Status.DISPATCHED && dispatch.getStatus() != Status.BACKUP_DISPATCHED) {
            return ResponseEntity.badRequest().body("Recall can only be approved for a DISPATCHED or BACKUP_DISPATCHED dispatch. Current status: " + dispatch.getStatus());
        }

        // 5. For backup dispatches, verify the associated backup record exists.
        // Never fall back to dispatch.getAssignedVehicle()/Personnel() because those reflect the original
        // mission reservation, not the backup. If missing, fail safely without inventing a reservation.
        boolean isBackup = dispatch.getStatus() == Status.BACKUP_DISPATCHED;
        backupEntity backup = null;
        int vehiclesToRestore = 0;
        int personnelToRestore = 0;

        if (isBackup) {
            backup = backupRepo.findByDispatchId(dispatch.getDispatchId());
            if (backup == null) {
                return ResponseEntity.status(404).body("Associated backup record not found.");
            }
            vehiclesToRestore = backup.getReqVehicles() != null ? backup.getReqVehicles() : 0;
            personnelToRestore = backup.getReqPersonnel() != null ? backup.getReqPersonnel() : 0;
        } else {
            vehiclesToRestore = dispatch.getAssignedVehicle() != null ? dispatch.getAssignedVehicle() : 0;
            personnelToRestore = dispatch.getAssignedPersonnel() != null ? dispatch.getAssignedPersonnel() : 0;
        }

        // 6. Atomically transition recall status from PENDING to APPROVED to prevent concurrent double-restoration
        if (mongoTemplate != null) {
            Query query = new Query(Criteria.where("_id").is(recallId).and("status").is(recallStatus.PENDING));
            Update update = new Update()
                    .set("status", recallStatus.APPROVED)
                    .set("decidedAt", LocalDateTime.now());
            recallRequestEntity atomicRecall = mongoTemplate.findAndModify(
                    query, update, FindAndModifyOptions.options().returnNew(true), recallRequestEntity.class);

            if (atomicRecall == null) {
                recallRequestEntity current = recallRequestRepository.findById(recallId).orElse(null);
                if (current != null && current.getStatus() != recallStatus.PENDING) {
                    return ResponseEntity.badRequest().body("This recall request has already been " + current.getStatus().name().toLowerCase() + ".");
                }
                return ResponseEntity.badRequest().body("This recall request is currently being processed or has already been decided.");
            }
            recall = atomicRecall;
        } else {
            recall.setStatus(recallStatus.APPROVED);
            recall.setDecidedAt(LocalDateTime.now());
            recallRequestRepository.save(recall);
        }

        // 7. Update backup record status if applicable
        if (backup != null) {
            backup.setStatus(backupStatus.CANCELLED);
            backupRepo.save(backup);
        }

        // 8. Restore resources to the station that owns them (guaranteed exactly once)
        resourceEntity resource = resourceRepo.findByStationId(dispatch.getStationId());
        if (resource != null) {
            resource.setAvailableVehicle(resource.getAvailableVehicle() + vehiclesToRestore);
            resource.setAvailablePersonnel(resource.getAvailablePersonnel() + personnelToRestore);
            resourceRepo.save(resource);
        }

        // 9. Return assigned worker to AVAILABLE status
        if (dispatch.getWorkerId() != null) {
            userEntity workerUser = userRepository.findById(dispatch.getWorkerId()).orElse(null);
            if (workerUser != null) {
                workerUser.setWorkerStatus(Admin.AVAILABLE);
                userRepository.save(workerUser);
            }
        }

        // 10. Mark dispatch as RECALLED and persist before stopping simulation & broadcasting
        dispatch.setStatus(Status.RECALLED);
        dispatch.setSimulationActive(false);
        dispatch.setCompletedAt(LocalDateTime.now());
        dispatchRepository.save(dispatch);

        // 11. Stop the scheduled simulation task AFTER persisting the terminal status
        simulationService.stopDispatch(dispatch.getDispatchId());

        // 12. Broadcast location/status update so frontend map and subscribers remove the vehicle marker
        simulationService.broadcastStatus(dispatch);

        // 13. Build the notification DTO
        recallRequestDto dto = new recallRequestDto(
                recallId.toHexString(),
                dispatch.getDispatchId().toHexString(),
                dispatch.getDisasterId() != null ? dispatch.getDisasterId().toHexString() : null,
                station.getStationId().toHexString(),
                station.getStationName(),
                dispatch.getForceType(),
                recall.getRequestingWorkerUsername(),
                recall.getReason(),
                recall.getRequestedAt(),
                recallStatus.APPROVED,
                Status.RECALLED
        );

        // 14. Notify manager (confirmation)
        messagingTemplate.convertAndSendToUser(manager.getUsername(), "/update/mission", dto);

        // 15. Notify requesting worker
        if (recall.getRequestingWorkerUsername() != null) {
            messagingTemplate.convertAndSendToUser(recall.getRequestingWorkerUsername(), "/new/mission", dto);
        }

        // 16. Notify the assigned admin so the dashboard reflects the change
        if (dispatch.getDisasterId() != null) {
            disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());
            if (disaster != null && disaster.getAssignedAdminId() != null) {
                userEntity adminUser = userRepository.findById(disaster.getAssignedAdminId()).orElse(null);
                if (adminUser != null && adminUser.getUsername() != null) {
                    reportResponse adminNotice = new reportResponse(
                            dispatch.getDisasterId().toHexString(),
                            "Force recall approved: " + (dispatch.getForceType() != null ? dispatch.getForceType().name() : "force") + " recalled from disaster.",
                            disaster.getStatus()
                    );
                    messagingTemplate.convertAndSendToUser(adminUser.getUsername(), "/topic/disaster", adminNotice);
                }
            }
        }

        return ResponseEntity.ok("Recall approved. Dispatch " + dispatch.getDispatchId().toHexString() + " has been recalled.");
    }

    @Override
    public ResponseEntity<?> rejectRecall(String username, ObjectId recallId) {

        // 1. Load the recall request
        recallRequestEntity recall = recallRequestRepository.findById(recallId).orElse(null);
        if (recall == null) {
            return ResponseEntity.status(404).body("Recall request not found.");
        }

        // 2. Only PENDING requests can be rejected
        if (recall.getStatus() != recallStatus.PENDING) {
            return ResponseEntity.badRequest().body("This recall request has already been " + recall.getStatus().name().toLowerCase() + ".");
        }

        // 3. Validate manager authorization
        userEntity manager = userRepository.findByUsername(username);
        if (manager == null) {
            return ResponseEntity.status(404).body("Manager not found.");
        }
        stationEntity station = stationRepository.findById(recall.getStationId()).orElse(null);
        if (station == null) {
            return ResponseEntity.status(404).body("Station not found.");
        }
        if (!station.getUserId().equals(manager.getUserId())) {
            return ResponseEntity.status(403).body("You are not authorized to reject this recall request.");
        }

        // 4. Atomically transition recall status from PENDING to REJECTED
        if (mongoTemplate != null) {
            Query query = new Query(Criteria.where("_id").is(recallId).and("status").is(recallStatus.PENDING));
            Update update = new Update()
                    .set("status", recallStatus.REJECTED)
                    .set("decidedAt", LocalDateTime.now());
            recallRequestEntity atomicRecall = mongoTemplate.findAndModify(
                    query, update, FindAndModifyOptions.options().returnNew(true), recallRequestEntity.class);

            if (atomicRecall == null) {
                recallRequestEntity current = recallRequestRepository.findById(recallId).orElse(null);
                if (current != null && current.getStatus() != recallStatus.PENDING) {
                    return ResponseEntity.badRequest().body("This recall request has already been " + current.getStatus().name().toLowerCase() + ".");
                }
                return ResponseEntity.badRequest().body("This recall request is currently being processed or has already been decided.");
            }
            recall = atomicRecall;
        } else {
            recall.setStatus(recallStatus.REJECTED);
            recall.setDecidedAt(LocalDateTime.now());
            recallRequestRepository.save(recall);
        }

        // 5. Notify the requesting worker — dispatch is NOT touched
        dispatchEntity dispatch = dispatchRepository.findById(recall.getDispatchId()).orElse(null);
        if (recall.getRequestingWorkerUsername() != null) {
            recallRequestDto dto = new recallRequestDto(
                    recallId.toHexString(),
                    recall.getDispatchId().toHexString(),
                    recall.getDisasterId() != null ? recall.getDisasterId().toHexString() : null,
                    station.getStationId().toHexString(),
                    station.getStationName(),
                    recall.getForceType(),
                    recall.getRequestingWorkerUsername(),
                    recall.getReason(),
                    recall.getRequestedAt(),
                    recallStatus.REJECTED,
                    dispatch != null ? dispatch.getStatus() : null
            );
            messagingTemplate.convertAndSendToUser(recall.getRequestingWorkerUsername(), "/new/mission", dto);
        }

        return ResponseEntity.ok("Recall request rejected. The dispatch continues its current lifecycle.");
    }

    private recallRequestDto mapToRecallDto(recallRequestEntity recall) {
        if (recall == null) return null;
        String stationName = "Unknown Station";
        if (recall.getStationId() != null) {
            stationEntity station = stationRepository.findById(recall.getStationId()).orElse(null);
            if (station != null && station.getStationName() != null) {
                stationName = station.getStationName();
            }
        }
        Status dStatus = null;
        if (recall.getDispatchId() != null) {
            dispatchEntity dispatch = dispatchRepository.findById(recall.getDispatchId()).orElse(null);
            if (dispatch != null) {
                dStatus = dispatch.getStatus();
            }
        }
        return new recallRequestDto(
                recall.getRecallId() != null ? recall.getRecallId().toHexString() : null,
                recall.getDispatchId() != null ? recall.getDispatchId().toHexString() : null,
                recall.getDisasterId() != null ? recall.getDisasterId().toHexString() : null,
                recall.getStationId() != null ? recall.getStationId().toHexString() : null,
                stationName,
                recall.getForceType(),
                recall.getRequestingWorkerUsername(),
                recall.getReason(),
                recall.getRequestedAt(),
                recall.getStatus(),
                dStatus
        );
    }

    @Override
    public ResponseEntity<?> getPendingRecallsForManager(String username) {
        userEntity manager = userRepository.findByUsername(username);
        if (manager == null) {
            return ResponseEntity.status(404).body("Manager not found.");
        }
        if (manager.getStationId() == null) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
        List<recallRequestEntity> pendingRecalls = recallRequestRepository
                .findByStationIdAndStatus(manager.getStationId(), recallStatus.PENDING);
        List<recallRequestDto> dtos = pendingRecalls.stream()
                .map(this::mapToRecallDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<?> getActiveRecallsForWorker(String username) {
        userEntity worker = userRepository.findByUsername(username);
        if (worker == null) {
            return ResponseEntity.status(404).body("Worker not found.");
        }
        List<recallRequestEntity> workerRecalls = recallRequestRepository
                .findByRequestingWorkerUsername(username);
        List<recallRequestDto> dtos = workerRecalls.stream()
                .map(this::mapToRecallDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }
}