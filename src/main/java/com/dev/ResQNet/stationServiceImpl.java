package com.dev.ResQNet;


import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;


@Service
public class stationServiceImpl implements stationService {
    
    @Autowired
    private stationRepo stationRepository;

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
}
