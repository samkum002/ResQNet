package com.dev.ResQNet;


import java.time.LocalDateTime;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;


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
            stationEntity station = stationRepository.findNearestStationByLocationAndStatusAndForceType(disaster.getLocation(),Station.ACTIVE,force);
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

        messagingTemplate.convertAndSend("/topic/disaster" + disaster.getAssignedAdminId(),new reportResponse(disaster.getDisasterId(),"Dispatch approved.",disaster.getStatus()));
        messagingTemplate.convertAndSend("/queue/report" + disaster.getUserId(),new reportResponse(disaster.getDisasterId(),"Dispatch approved.",disaster.getStatus()));
        return ResponseEntity.ok("Dispatch approved successfully.");
    }
}
