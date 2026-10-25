package com.dev.ResQNet;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class adminDashboardServiceImpl implements adminDashboardService{

    @Autowired
    adminDashboardRepo dashboardRepo;

    @Autowired
    stationRepo stationrepo;

    @Autowired
    SimpMessagingTemplate template;

    @Autowired
    userRepo userrepo;

    @Autowired
    disasterRepo DisasterRepo;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private stationService stationservice;

    @Autowired
    private ReverseGeocodingService reverseGeocoding;
    
    @Override
    public void checkInfo(ObjectId disasterId){
        disasterEntity entity = DisasterRepo.findByDisasterId(disasterId);
        if(entity != null && entity.getAiStatus() == AI.COMPLETED){
            if (entity.getState() == null && entity.getLocation() != null) {
                reverseGeocoding.findAndSaveState(entity, entity.getLocation().getY(), entity.getLocation().getX());
            }
            findAdmin(disasterId);
        }
    }

    @Override
    public void findAdmin(ObjectId disasterId){
        disasterEntity entity = DisasterRepo.findByDisasterId(disasterId);
        ObjectId userId = entity.getUserId();
        userEntity user = userrepo.findByUserId(userId);
        entity.setAssignmentStatus(Assignment.PROCESSING);
        List<userEntity> admins = dashboardRepo.findByRolesContainingAndAdminStateAndAdminStatus("ADMIN",entity.getState(),Admin.AVAILABLE);
        if(admins.isEmpty()){
            admins = dashboardRepo.findByRolesContainingAndAdminStatus("ADMIN", Admin.AVAILABLE);
        }
        if(admins.isEmpty()){
            admins = dashboardRepo.findByRolesContaining("ADMIN");
        }
        if(admins.isEmpty()){
            entity.setAssignmentStatus(Assignment.TIMEOUT);
            entity.setStatus(Status.UNDER_REVIEW);
            DisasterRepo.save(entity);
            return;
        }
        userEntity sortedAdmin = admins.stream().min(Comparator.comparingInt(userEntity::getActiveIncidents)).orElseThrow();
        ObjectId adminId = sortedAdmin.getUserId();
        entity.setAssignedAdminId(adminId);
        entity.setAssignedAt(LocalDateTime.now());
        entity.setStatus(Status.UNDER_REVIEW);
        entity.setAssignmentStatus(Assignment.ASSIGNED);
        sortedAdmin.setActiveIncidents(sortedAdmin.getActiveIncidents()+1);
        DisasterRepo.save(entity);
        template.convertAndSendToUser(user.getUsername(), "/queue/report", new reportResponse(entity.getDisasterId().toHexString(),"Disaster is under review.",entity.getStatus()));
        if(sortedAdmin.getActiveIncidents()>3){
            sortedAdmin.setAdminStatus(Admin.BUSY);
        }
        userrepo.save(sortedAdmin);
        disasterDto dto = new disasterDto();
        dto.setDisasterId(entity.getDisasterId().toHexString());
        dto.setSuspicious(entity.getSuspicious());
        dto.setAiConfidence(entity.getAiConfidence());
        dto.setAiStatus(entity.getAiStatus());
        dto.setStatus(entity.getStatus());
        dto.setUserReport(entity.getUserReport());
        dto.setAssignmentStatus(entity.getAssignmentStatus());
        dto.setDisasterType(entity.getDisasterType());
        dto.setForces(entity.getForces());
        dto.setFinalConfidence(entity.getFinalConfidence());
        dto.setImage(entity.getImage().toHexString());
        dto.setState(entity.getState());
        dto.setSeverity(entity.getSeverity());
        dto.setSuspicious(entity.getSuspicious());
        dto.setReportCount(entity.getReportCount()); 
        template.convertAndSendToUser(sortedAdmin.getUsername(), "/topic/disaster", dto);

    }

    @Override
    @Scheduled(cron="*/30 * * * * *")
    public void reassignDisaster(){
        List<disasterEntity> disasters = DisasterRepo.findByAssignmentStatus(Assignment.ASSIGNED);
        for(disasterEntity entity : disasters){
            if(entity.getAssignedAt() != null && entity.getAssignedAt().plusMinutes(3).isBefore(LocalDateTime.now())){
                entity.setAssignmentStatus(Assignment.TIMEOUT);
                DisasterRepo.save(entity);
            }
        }
        List<disasterEntity> Alldisasters = DisasterRepo.findByAssignmentStatus(Assignment.TIMEOUT);
        for(disasterEntity entity : Alldisasters){
            disasterDto dto = new disasterDto();
            dto.setDisasterId(entity.getDisasterId().toHexString());
            dto.setSuspicious(entity.getSuspicious());
            dto.setAiConfidence(entity.getAiConfidence());
            dto.setAiStatus(entity.getAiStatus());
            dto.setDisasterType(entity.getDisasterType());
            dto.setUserReport(entity.getUserReport());
            dto.setForces(entity.getForces());
            dto.setFinalConfidence(entity.getFinalConfidence());
            dto.setImage(entity.getImage().toHexString());
            dto.setState(entity.getState());
            dto.setStatus(entity.getStatus());
            dto.setSeverity(entity.getSeverity());
            dto.setSuspicious(entity.getSuspicious());
            dto.setReportCount(entity.getReportCount());
            ObjectId adminId = entity.getAssignedAdminId();
            List<userEntity> admins = dashboardRepo.findByRolesContainingAndAdminState("ADMIN", entity.getState());
            if(admins.isEmpty()){
                admins = dashboardRepo.findByRolesContaining("ADMIN");
            }
            List<userEntity> filteredAdmins = admins.stream()
                .filter(admin -> admin.getAdminStatus() != Admin.OFFLINE && (adminId == null || !admin.getUserId().equals(adminId)))
                .sorted(Comparator.comparingInt(userEntity::getActiveIncidents))
                .toList();

            if (!filteredAdmins.isEmpty()) {
                userEntity chosenAdmin = filteredAdmins.get(0);
                entity.setAssignedAdminId(chosenAdmin.getUserId());
                entity.setStatus(Status.UNDER_REVIEW);
                entity.setAssignmentStatus(Assignment.REASSIGNED);
                entity.setAssignedAt(LocalDateTime.now());
                chosenAdmin.setActiveIncidents(chosenAdmin.getActiveIncidents() + 1);
                if (chosenAdmin.getActiveIncidents() > 3) {
                    chosenAdmin.setAdminStatus(Admin.BUSY);
                }
                DisasterRepo.save(entity);
                userrepo.save(chosenAdmin);

                dto.setStatus(entity.getStatus());
                dto.setAssignmentStatus(entity.getAssignmentStatus());
                template.convertAndSendToUser(chosenAdmin.getUsername(), "/topic/disaster", dto);
            }
        }
    }

    @Override
    public void calculateFinalVal(ObjectId disasterId){
        disasterEntity disaster = DisasterRepo.findByDisasterId(disasterId);
        userEntity user = userrepo.findByUserId(disaster.getUserId());
        Double trust = 0.2*user.getTrustScore();
        Double finalVal = ((0.5)*disaster.getAiConfidence()+trust+disaster.getReportCount()*0.3);
        if(finalVal<55){
            disaster.setSuspicious(true);
        }
        disaster.setFinalConfidence(finalVal);
        DisasterRepo.save(disaster);
    }

    @Override
    public boolean checkDuplicateDisasters(double latitude, double longitude, String state, ObjectId disasterId) {
        LocalDateTime ctf = LocalDateTime.now().minusHours(24);
        Query query = new Query();
        if (state != null && !state.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("state").is(state),
                    Criteria.where("state").is(null),
                    Criteria.where("state").exists(false)
            ));
        }
        query.addCriteria(Criteria.where("createdAt").gte(ctf));

        // Eligible statuses for duplicate matching: active in-progress and completed disasters.
        // Exclude ALREADY_PRESENT (duplicates themselves), and cancelled/fake disasters.
        List<Status> eligibleStatuses = List.of(
                Status.REPORTED, Status.AI_PROGRESS, Status.UNDER_REVIEW, Status.VERIFIED,
                Status.DISPATCH_ASSIGNED, Status.DISPATCHED, Status.BACKUP_REQUEST,
                Status.BACKUP_ASSIGNED, Status.BACKUP_DISPATCHED, Status.DISPATCH_COMPLETED,
                Status.BACKUP_DISPATCH_COMPLETED, Status.COMPLETED
        );
        query.addCriteria(Criteria.where("status").in(eligibleStatuses));

        // Disaster location is indexed with GEO_2DSPHERE GeoJsonPoint, which requires distance in METERS (500m)
        query.addCriteria(Criteria.where("location").nearSphere(new GeoJsonPoint(longitude, latitude)).maxDistance(500.0));

        List<disasterEntity> disasters = mongoTemplate.find(query, disasterEntity.class);
        if (disasters.isEmpty()) {
            return false;
        }

        disasterEntity dnew = DisasterRepo.findByDisasterId(disasterId);
        if (dnew == null || dnew.getUserReport() == null) {
            return false;
        }

        for (disasterEntity entity : disasters) {
            if (entity.getDisasterId().equals(dnew.getDisasterId())) {
                continue;
            }

            if (entity.getUserReport() != null && entity.getUserReport().trim().equalsIgnoreCase(dnew.getUserReport().trim())) {
                // Ensure duplicate links to the root original disaster
                ObjectId rootTargetId = (entity.getLinkedDisasterId() != null)
                        ? entity.getLinkedDisasterId()
                        : entity.getDisasterId();
                disasterEntity rootDisaster = DisasterRepo.findByDisasterId(rootTargetId);
                if (rootDisaster == null) {
                    rootDisaster = entity;
                }

                rootDisaster.setReportCount((rootDisaster.getReportCount() != null ? rootDisaster.getReportCount() : 1) + 1);
                DisasterRepo.save(rootDisaster);
                calculateFinalVal(rootDisaster.getDisasterId());

                dnew.setStatus(Status.ALREADY_PRESENT);
                dnew.setAssignmentStatus(Assignment.DUPLICATE);
                dnew.setAssignedAdminId(rootDisaster.getAssignedAdminId());
                dnew.setLinkedDisasterId(rootDisaster.getDisasterId());
                DisasterRepo.save(dnew);

                duplicateDisasterDto dto = new duplicateDisasterDto();
                dto.setDisasterId(rootDisaster.getDisasterId());
                dto.setReportCount(rootDisaster.getReportCount());
                dto.setFinalConfidence(rootDisaster.getFinalConfidence());

                if (dnew.getAssignedAdminId() != null) {
                    userEntity assignedAdmin = userrepo.findByUserId(dnew.getAssignedAdminId());
                    if (assignedAdmin != null && assignedAdmin.getUsername() != null) {
                        template.convertAndSendToUser(assignedAdmin.getUsername(), "/topic/disaster", dto);
                    }
                }
                template.convertAndSend("/topic/disaster", dto);

                userEntity duplicateReporter = userrepo.findByUserId(dnew.getUserId());
                reportResponse dupMsg = rootDisaster.getStatus() == Status.COMPLETED
                        ? new reportResponse(dnew.getDisasterId().toHexString(), "Disaster is already reported and completed.", rootDisaster.getStatus())
                        : new reportResponse(dnew.getDisasterId().toHexString(), "Disaster is already reported.", rootDisaster.getStatus());
                if (duplicateReporter != null && duplicateReporter.getUsername() != null) {
                    template.convertAndSendToUser(duplicateReporter.getUsername(), "/queue/report", dupMsg);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public ResponseEntity<adminDashboard> disasterList(ObjectId userId){
        userEntity user = userrepo.findByUserId(userId);
        if (user != null && user.getAdminState() != null) {
            List<disasterEntity> unassigned = DisasterRepo.findByAssignmentStatusAndState(Assignment.TIMEOUT, user.getAdminState());
            for (disasterEntity un : unassigned) {
                un.setAssignedAdminId(userId);
                un.setStatus(Status.UNDER_REVIEW);
                un.setAssignmentStatus(Assignment.ASSIGNED);
                un.setAssignedAt(LocalDateTime.now());
                DisasterRepo.save(un);
                user.setActiveIncidents(user.getActiveIncidents() + 1);
                userrepo.save(user);
            }
        }
        List<disasterEntity> disasters = DisasterRepo.findByAssignedAdminIdAndStatus(userId, Status.UNDER_REVIEW);
        List<disasterDto> dtos = new ArrayList<>();
        for(disasterEntity entity : disasters){
            disasterDto dto = new disasterDto();
            dto.setAiConfidence(entity.getAiConfidence());
            dto.setAiStatus(entity.getAiStatus());
            dto.setDisasterType(entity.getDisasterType());
            dto.setFinalConfidence(entity.getFinalConfidence());
            dto.setForces(entity.getForces());
            dto.setSeverity(entity.getSeverity());
            dto.setStatus(entity.getStatus());
            dto.setReportCount(entity.getReportCount());
            dto.setState(entity.getState());
            dto.setAssignmentStatus(entity.getAssignmentStatus());
            dto.setSuspicious(entity.getSuspicious());
            dto.setImage(entity.getImage().toHexString());
            dto.setDisasterId(entity.getDisasterId().toHexString());
            dto.setUserReport(entity.getUserReport());
            dtos.add(dto);
        }
        Stats stats = user != null ? user.getStats() : null;
        return ResponseEntity.ok(new adminDashboard(stats, dtos));
    }

    
    @Override
    @Transactional
    public ResponseEntity<?> disasterApprove(ObjectId disasterId){
        disasterEntity disaster = DisasterRepo.findByDisasterId(disasterId);
        if(disaster == null){
            return ResponseEntity.notFound().build();
        }
        if(disaster.getStatus() == Status.VERIFIED || disaster.getStatus() == Status.REJECTED){
            return ResponseEntity.badRequest().body("Disaster has already been processed.");
        }
        userEntity user = userrepo.findByUserId(disaster.getUserId());
        Double conf = disaster.getFinalConfidence();
        Double multiplier = 0.0;
        // Set<Forces> forces = disaster.getForces();
        Severity severity = disaster.getSeverity();
        Integer trucks = 0;
        Integer personnel = 0;
        switch(severity){
            case LOW -> {
                trucks  = 1;
                personnel = 4;
            }case MEDIUM -> {
                trucks = 2;
                personnel = 8;
            }case HIGH -> {
                trucks = 4;
                personnel = 15;
            }case CRITICAL -> {
                trucks = 6;
                personnel = 25;
            }
        }
        if(conf<40){
            multiplier=0.5;
        }else if(conf<60){
            multiplier=0.7;
        }else if(conf<75){
            multiplier=0.85;
        }else if(conf<90){
            multiplier=1.0;
        }else{
            multiplier = 1.2;
        }
        Integer newTrucks = (int) Math.ceil((trucks*multiplier));
        Integer newPersonnel = (int) Math.ceil((personnel*multiplier));
        boolean duplicate = checkDuplicateDisasters(disaster.getLocation().getY(), disaster.getLocation().getX(), disaster.getState(), disasterId);
        if(duplicate){
            userEntity admin = userrepo.findByUserId(disaster.getAssignedAdminId());
            admin.setActiveIncidents(admin.getActiveIncidents()-1);
            userrepo.save(admin);
            return ResponseEntity.ok(new reportResponse(disaster.getDisasterId().toHexString(),"Duplicate report found.",disaster.getStatus()));
        }
        disaster.setStatus(Status.VERIFIED);
        disaster.setAssignmentStatus(Assignment.APPROVED);
        DisasterRepo.save(disaster);
        stationservice.findStation(disasterId, newTrucks, newPersonnel);
        template.convertAndSendToUser(user.getUsername(), "/queue/report", new reportResponse(disaster.getDisasterId().toHexString(), "Disaster is verified.",disaster.getStatus()));
        return ResponseEntity.ok(new reportResponse(disaster.getDisasterId().toHexString(),"Disaster has been verified. Help is on the way.",disaster.getStatus()));
    }
    
    @Override
    @Transactional
    public ResponseEntity<?> disasterReject(ObjectId disasterId){
        disasterEntity disaster = DisasterRepo.findByDisasterId(disasterId);
        if(disaster==null){
            return ResponseEntity.notFound().build();
        }
        if(disaster.getStatus() == Status.VERIFIED || disaster.getStatus() == Status.REJECTED){
            return ResponseEntity.badRequest().body("Disaster has already been processed.");
        }
        disaster.setAssignmentStatus(Assignment.REJECTED);
        disaster.setStatus(Status.REJECTED);
        DisasterRepo.save(disaster);
        userEntity user = userrepo.findByUserId(disaster.getUserId());
        user.setTrustScore(Math.max(0,user.getTrustScore()-20));
        userrepo.save(user);
        userEntity admin = userrepo.findByUserId(disaster.getAssignedAdminId());
        admin.setActiveIncidents(admin.getActiveIncidents()-1);
        Stats stat = admin.getStats();
        stat.setFalseReports(stat.getFalseReports() + 1);
        stat.setTotalAssigned(stat.getTotalAssigned() + 1);
        admin.setStats(stat);
        userrepo.save(admin);
        template.convertAndSendToUser(user.getUsername(), "/queue/report", new reportResponse(disaster.getDisasterId().toHexString(),
        "Disaster is rejected. Kindly don't spam the management system",disaster.getStatus()));
        return ResponseEntity.ok(new reportResponse(disaster.getDisasterId().toHexString(), "Disaster has been rejected", disaster.getStatus()));   
    }

}
