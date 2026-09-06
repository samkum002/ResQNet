package com.dev.ResQNet;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
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
    private resourceRepository resourceRepo;

    @Autowired
    private dispatchRepo dispatchRepository;

    @Autowired
    private userRepo userRepository;

    @Autowired
    private disasterRepo disasterRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

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
                dto.setDispatchId(dispatch.getDispatchId());
                dto.setSeverity(dispatch.getSeverity());
                dto.setForceType(dispatch.getForceType());
                dto.setAssignedVehicle(dispatch.getAssignedVehicle());
                dto.setAssignedPersonnel(dispatch.getAssignedPersonnel());
                dto.setStatus(dispatch.getStatus());
                messagingTemplate.convertAndSend("/update/mission" + station.getUserId(), dto);
                newTrucks -= requiredTrucks;
                newPersonnel -= requiredPersonnel;
            }
            index++;
        }
    }

    @Override
    public ResponseEntity<List<dispatchDto>> getMissionsForStation(String username) {
        userEntity user = userRepository.findByUsername(username);
        List<dispatchEntity> dispatches = dispatchRepository.findByStationIdAndStatus(user.getStationId(), Status.VERIFIED);
        List<dispatchDto> dispatchDtos = new ArrayList<>();
        for(dispatchEntity dispatch : dispatches){
            dispatchDto dto = new dispatchDto();
            dto.setDispatchId(dispatch.getDispatchId());
            dto.setSeverity(dispatch.getSeverity());
            dto.setForceType(dispatch.getForceType());
            dto.setAssignedVehicle(dispatch.getAssignedVehicle());
            dto.setAssignedPersonnel(dispatch.getAssignedPersonnel());
            dto.setStatus(dispatch.getStatus());
            dispatchDtos.add(dto);
        }
        if(dispatchDtos.isEmpty()){
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(dispatchDtos);

    }

    @Override
    @Transactional
    public ResponseEntity<?> approveMisssion(ObjectId dispatchId,String username) {

        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElseThrow(() ->new RuntimeException("Dispatch not found"));

        if (!dispatch.getStationId().equals(userRepository.findByUsername(username).getStationId())) {
            return ResponseEntity.status(403).body("You are not authorized to approve this mission.");
        }

        if (dispatch.getStatus() == Status.DISPATCHED) {
            return ResponseEntity.badRequest().body("This mission has already been processed.");
        }

        resourceEntity resource = resourceRepo.findById(dispatch.getStationId()).orElseThrow(() ->new RuntimeException("Station resource not found"));
        disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());

        stationEntity station = stationRepository.findById(dispatch.getStationId()).orElseThrow(() ->new RuntimeException("Station not found"));

        if (disaster == null) {
            return ResponseEntity.badRequest().body("Disaster not found.");
        }

        if (resource.getAvailablePersonnel() < dispatch.getAssignedPersonnel()) {
            return ResponseEntity.badRequest().body("Not enough available personnel.");
        }

        if (resource.getAvailableVehicle() < dispatch.getAssignedVehicle()) {
            return ResponseEntity.badRequest().body("Not enough available vehicles.");
        }

        resource.setAvailablePersonnel(resource.getAvailablePersonnel()- dispatch.getAssignedPersonnel());
        resource.setAvailableVehicle(resource.getAvailableVehicle()- dispatch.getAssignedVehicle());
        resourceRepo.save(resource);

        dispatch.setStatus(Status.DISPATCHED);
        dispatch.setDispatchedAt(LocalDateTime.now());
        dispatchRepository.save(dispatch);

        disaster.setStatus(Status.DISPATCHED);
        disaster.setDispatchedAt(dispatch.getDispatchedAt());
        disasterRepository.save(disaster);

        messagingTemplate.convertAndSend("/new/mission" + station.getWorkerId(), new dispatchDto(dispatch.getSeverity(),dispatch.getDispatchId(),dispatch.getForceType(),dispatch.getAssignedVehicle(),dispatch.getAssignedPersonnel(),dispatch.getStatus()));
        messagingTemplate.convertAndSend("/topic/disaster" + disaster.getAssignedAdminId(),new reportResponse(disaster.getDisasterId(),"Dispatch approved.",disaster.getStatus()));
        messagingTemplate.convertAndSend("/queue/report" + disaster.getUserId(),new reportResponse(disaster.getDisasterId(),"Dispatch approved.",disaster.getStatus()));
        return ResponseEntity.ok("Dispatch approved successfully.");
    }

    @Override
    @Async
    public void newMission(ObjectId dispatchId, String username, Forces force){
        
        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElseThrow(() -> new RuntimeException("Dispatch not found"));
        disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());
        
        List<stationEntity> stations = stationRepository.findByStatusAndForceTypeAndStationIdNotInAndLocationNear(Station.ACTIVE,force,disaster.getRejectedStations(),disaster.getLocation());
        stations.sort((a, b) -> {
            double distanceA = calculateDistance(disaster.getLocation(),a.getLocation());
            double distanceB = calculateDistance(disaster.getLocation(),b.getLocation());
            return Double.compare(distanceA, distanceB);
        });

        if (stations.isEmpty()) {
            disaster.setStatus(Status.DISPATCH_FAILED);
            disasterRepository.save(disaster);
            messagingTemplate.convertAndSend("/queue/report" + disaster.getUserId(),new reportResponse(disaster.getDisasterId(),"No available stations for the required force type -> " + force,disaster.getStatus()));
            return;
        }

        stationEntity station = stations.get(0);
        dispatchEntity newDispatch = new dispatchEntity();
        newDispatch.setDisasterId(disaster.getDisasterId());
        newDispatch.setStationId(station.getStationId());
        newDispatch.setAssignedVehicle(dispatch.getAssignedVehicle());
        newDispatch.setAssignedPersonnel(dispatch.getAssignedPersonnel());
        newDispatch.setForceType(force);
        newDispatch.setSeverity(disaster.getSeverity());
        newDispatch.setStatus(Status.VERIFIED);
        dispatchRepository.save(newDispatch);

        dispatchDto dto = new dispatchDto();
        dto.setDispatchId(newDispatch.getDispatchId());
        dto.setSeverity(newDispatch.getSeverity());
        dto.setForceType(newDispatch.getForceType());
        dto.setAssignedVehicle(newDispatch.getAssignedVehicle());
        dto.setAssignedPersonnel(newDispatch.getAssignedPersonnel());
        dto.setStatus(newDispatch.getStatus());
        messagingTemplate.convertAndSend("/update/mission" + station.getUserId(), dto);        
    }

    @Override 
    @Transactional
    public ResponseEntity<?> completeMission(ObjectId dispatchId, String username) {

        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElseThrow(() -> new RuntimeException("Dispatch not found"));
        userEntity worker = userRepository.findByUsername(username);

        if (!dispatch.getStationId().equals(worker.getStationId())){
            return ResponseEntity.status(403).body("You are not authorized to complete this mission.");
        }
        if (dispatch.getStatus() != Status.DISPATCHED) {
            return ResponseEntity.badRequest().body("This mission is not in a state that can be completed.");
        }
        resourceEntity resource = resourceRepo.findById(dispatch.getStationId()).orElseThrow(() -> new RuntimeException("Station resource not found"));
        resource.setAvailablePersonnel(resource.getAvailablePersonnel() + dispatch.getAssignedPersonnel());
        resource.setAvailableVehicle(resource.getAvailableVehicle() + dispatch.getAssignedVehicle());
        resourceRepo.save(resource);

        dispatch.setStatus(Status.COMPLETED);
        dispatch.setCompletedAt(LocalDateTime.now());
        dispatchRepository.save(dispatch);

        disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());
        disaster.setStatus(Status.COMPLETED);
        disaster.setCompletedAt(dispatch.getCompletedAt());
        disasterRepository.save(disaster);

        userEntity admin = userRepository.findById(disaster.getAssignedAdminId()).orElseThrow(() -> new RuntimeException("User not found"));
        admin.setActiveIncidents(admin.getActiveIncidents()-1);
        userRepository.save(admin);

        userEntity user = userRepository.findById(disaster.getUserId()).orElseThrow(() -> new RuntimeException("User not found"));
        user.setTrustScore(Math.max(0, user.getTrustScore() + 10));
        userRepository.save(user);
        
        messagingTemplate.convertAndSend("/topic/disaster" + disaster.getAssignedAdminId(), new reportResponse(disaster.getDisasterId(), "Mission completed.", disaster.getStatus()));
        messagingTemplate.convertAndSend("/queue/report" + disaster.getUserId(), new reportResponse(disaster.getDisasterId(), "Mission completed.", disaster.getStatus()));
        return ResponseEntity.ok("Dispatch completed successfully.");
    }

    @Override
    @Transactional 
    public ResponseEntity<?> fakeMission(ObjectId dispatchId, String username) {
        
        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElseThrow(() -> new RuntimeException("Dispatch not found"));
        userEntity worker = userRepository.findByUsername(username);

        if (!dispatch.getStationId().equals(worker.getStationId())){
            return ResponseEntity.status(403).body("You are not authorized to mark this mission as fake.");
        }
        if (dispatch.getStatus() != Status.DISPATCHED) {
            return ResponseEntity.badRequest().body("This mission is not in a state that can be marked as fake.");
        }

        resourceEntity resource = resourceRepo.findById(dispatch.getStationId()).orElseThrow(() -> new RuntimeException("Station resource not found"));
        resource.setAvailablePersonnel(resource.getAvailablePersonnel() + dispatch.getAssignedPersonnel());
        resource.setAvailableVehicle(resource.getAvailableVehicle() + dispatch.getAssignedVehicle());
        resourceRepo.save(resource);

        dispatch.setStatus(Status.DISPATCH_FAILED);
        dispatch.setCompletedAt(LocalDateTime.now());
        dispatchRepository.save(dispatch);

        disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());
        disaster.setStatus(Status.DISPATCH_FAILED);
        disaster.setCompletedAt(dispatch.getCompletedAt());
        disasterRepository.save(disaster);

        userEntity admin = userRepository.findById(disaster.getAssignedAdminId()).orElseThrow(() -> new RuntimeException("User not found"));
        admin.setActiveIncidents(admin.getActiveIncidents()-1);
        userRepository.save(admin);

        userEntity user = userRepository.findById(disaster.getUserId()).orElseThrow(() -> new RuntimeException("User not found"));
        user.setTrustScore(Math.max(0, user.getTrustScore() - 20));
        userRepository.save(user);
        
        messagingTemplate.convertAndSend("/topic/disaster" + disaster.getAssignedAdminId(), new reportResponse(disaster.getDisasterId(), "Mission marked as fake.", disaster.getStatus()));
        messagingTemplate.convertAndSend("/queue/report" + disaster.getUserId(), new reportResponse(disaster.getDisasterId(), "Mission marked as fake. Please do not spam this platform.", disaster.getStatus()));
        
        return ResponseEntity.ok("Dispatch marked as fake successfully.");
    }
}
