package com.dev.ResQNet;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<stationDashboard> getMissions(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return stationservice.getMissionsForStation(username);
    }

    @RequestMapping(value = "/{dispatchId}/approve", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> approveDispatch(@PathVariable ObjectId dispatchId){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return stationservice.approveMisssion(dispatchId,username);
    }

    @RequestMapping(value = "/{dispatchId}/reject", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> rejectDispatch(@PathVariable ObjectId dispatchId) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        userEntity manager = userRepository.findByUsername(username);
        if (manager == null) {
            return ResponseEntity.status(404).body("Manager not found.");
        }

        dispatchEntity dispatch = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new RuntimeException("Dispatch not found"));

        if (!dispatch.getStationId().equals(manager.getStationId())) {
            return ResponseEntity.status(403)
                    .body("You are not authorized to reject this dispatch.");
        }

        Stats stats = manager.getStats();
        if (stats == null) {
            stats = new Stats(0, 0, 0, 0);
        }
        stats.setFalseReports(stats.getFalseReports() + 1);
        stats.setTotalAssigned(stats.getTotalAssigned() + 1);
        manager.setStats(stats);
        userRepository.save(manager);

        stationEntity station = stationRepository.findById(dispatch.getStationId())
                .orElseThrow(() -> new RuntimeException("Station not found"));

        disasterEntity disaster = disasterRepository.findByDisasterId(dispatch.getDisasterId());
        if (disaster != null) {
            disaster.getRejectedStations().add(station.getStationId());
            disasterRepository.save(disaster);
        }

        Forces force = station.getForceType();
        stationservice.newMission(dispatchId, force);

        return ResponseEntity.ok("Dispatch rejected successfully.");
    }

    @RequestMapping(value = "/{dispatchId}/complete", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> completeDispatch(@PathVariable ObjectId dispatchId){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return stationservice.completeMission(dispatchId,username);
    }

    @RequestMapping(value = "/{dispatchId}/fake", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> fakeDispatch(@PathVariable ObjectId dispatchId){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return stationservice.fakeMission(dispatchId,username);
    }

    /**
     * Station manager approves a pending recall request.
     * PATCH /station/recall/{recallId}/approve
     * Requires ROLE_STATION_MANAGER (enforced by SecurityConfig).
     */
    @RequestMapping(value = "/recall/{recallId}/approve", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> approveRecall(@PathVariable ObjectId recallId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return stationservice.approveRecall(username, recallId);
    }

    /**
     * Station manager rejects a pending recall request.
     * PATCH /station/recall/{recallId}/reject
     * Requires ROLE_STATION_MANAGER (enforced by SecurityConfig).
     */
    @RequestMapping(value = "/recall/{recallId}/reject", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<?> rejectRecall(@PathVariable ObjectId recallId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return stationservice.rejectRecall(username, recallId);
    }

}
