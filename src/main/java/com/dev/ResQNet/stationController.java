package com.dev.ResQNet;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/station")
public class stationController {
    
    @Autowired
    private stationService stationservice;

    @Autowired
    private stationRepo stationRepository;

    @Autowired
    private userRepo userRepository;

    @Autowired
    private dispatchRepo dispatchRepository;

    @Autowired
    private disasterRepo disasterRepository;

    @GetMapping("/missions")
    public ResponseEntity<List<dispatchDto>> getMissions(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return stationservice.getMissionsForStation(username);
    }

    @GetMapping("/{dispatchId}/approve")
    public ResponseEntity<?> approveDispatch(@PathVariable ObjectId dispatchId){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return stationservice.approveMisssion(dispatchId,username);
    }

    @GetMapping("/{dispatchId}/reject")
    public ResponseEntity<?> rejectDispatch(@PathVariable ObjectId dispatchId){

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        dispatchEntity dispatch = dispatchRepository.findById(dispatchId).orElseThrow(() -> new RuntimeException("Dispatch not found"));

        if (!dispatch.getStationId().equals(userRepository.findByUsername(username).getStationId())) {
            return ResponseEntity.status(403).body("You are not authorized to reject this dispatch.");
        }
        stationEntity station = stationRepository.findById(dispatch.getStationId()).orElseThrow(() -> new RuntimeException("Station not found"));
        disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());
        disaster.getRejectedStations().add(station.getStationId());
        disasterRepository.save(disaster);
        Forces force = station.getForceType();
        stationservice.newMission(dispatchId,username,force);

        return ResponseEntity.ok("Dispatch rejected successfully.");
    }

}
