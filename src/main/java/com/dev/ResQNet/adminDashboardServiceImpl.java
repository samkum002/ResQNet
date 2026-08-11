package com.dev.ResQNet;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

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
    
    @Override
    public void checkInfo(ObjectId disasterId){
        disasterEntity entity = DisasterRepo.findByDisasterId(disasterId);
        if(entity.getAiStatus()==AI.COMPLETED&&entity.getState()!=null){
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
            entity.setAssignmentStatus(Assignment.TIMEOUT);
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
        template.convertAndSendToUser(user.getUsername(), "/queue/report", new reportResponse(entity.getDisasterId(),"Disaster is under review.",entity.getStatus()));
        if(sortedAdmin.getActiveIncidents()>3){
            sortedAdmin.setAdminStatus(Admin.BUSY);
        }
        userrepo.save(sortedAdmin);
        disasterDto dto = new disasterDto();
        dto.setSuspicious(entity.getSuspicious());
        dto.setAiConfidence(entity.getAiConfidence());
        dto.setAiStatus(entity.getAiStatus());
        dto.setUserReport(entity.getUserReport());
        dto.setAssignmentStatus(entity.getAssignmentStatus());
        dto.setDisasterType(entity.getDisasterType());
        dto.setForces(entity.getForces());
        dto.setFinalConfidence(entity.getFinalConfidence());
        dto.setImage(entity.getImage());
        dto.setState(entity.getState());
        dto.setSeverity(entity.getSeverity());
        dto.setSuspicious(entity.getSuspicious());
        dto.setReportCount(entity.getReportCount());
        template.convertAndSend("/topic/Disaster/"+adminId, dto);

    }

    @Override
    @Scheduled(cron="*/30 * * * * *")
    public void reassignDisaster(){
        List<disasterEntity> disasters = DisasterRepo.findByAssignmentStatus(Assignment.ASSIGNED);
        for(disasterEntity entity : disasters){
            if(entity.getAssignedAt().plusMinutes(3).isBefore(LocalDateTime.now())){
                entity.setAssignmentStatus(Assignment.TIMEOUT);
                DisasterRepo.save(entity);
            }
        }
        List<disasterEntity> Alldisasters = DisasterRepo.findByAssignmentStatus(Assignment.TIMEOUT);
        for(disasterEntity entity : Alldisasters){
            disasterDto dto = new disasterDto();
            dto.setSuspicious(entity.getSuspicious());
            dto.setAiConfidence(entity.getAiConfidence());
            dto.setAiStatus(entity.getAiStatus());
            dto.setDisasterType(entity.getDisasterType());
            dto.setUserReport(entity.getUserReport());
            dto.setForces(entity.getForces());
            dto.setFinalConfidence(entity.getFinalConfidence());
            dto.setImage(entity.getImage());
            dto.setState(entity.getState());
            dto.setSeverity(entity.getSeverity());
            dto.setSuspicious(entity.getSuspicious());
            dto.setReportCount(entity.getReportCount());
            ObjectId adminId = entity.getAssignedAdminId();
            List<userEntity> admins = dashboardRepo.findByRolesContainingAndAdminState("ADMIN", entity.getState());
            List<userEntity> filteredAdmins = admins.stream().filter(admin->admin.getAdminStatus()!=Admin.OFFLINE).toList();
            for(userEntity admin : filteredAdmins){
                if(!admin.getUserId().equals(adminId)){
                    entity.setAssignmentStatus(Assignment.REASSIGNED);
                    DisasterRepo.save(entity);
                    dto.setAssignmentStatus(entity.getAssignmentStatus());
                    template.convertAndSend("/topic/Disaster/"+admin.getUserId(), dto);
                }
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
    public boolean checkDuplicateDisasters(double latitude,double longitude,String state,ObjectId disasterId){
        Query query = new Query();
        query.addCriteria(Criteria.where("state").is(state));
        query.addCriteria(Criteria.where("status").in(Status.UNDER_REVIEW,Status.AI_PROGRESS,Status.BACKUP_DISPATCHED,Status.DISPATCHED,Status.REPORTED,Status.VERIFIED));
        query.addCriteria(Criteria.where("location").nearSphere(new GeoJsonPoint(longitude, latitude)).maxDistance(500.0/6378137.0));
        List<disasterEntity> disasters = mongoTemplate.find(query,disasterEntity.class);
        if(disasters.isEmpty()){
            return false;
        }
        disasterEntity dnew = DisasterRepo.findByDisasterId(disasterId);
        for(disasterEntity entity : disasters){
            if(entity.getUserReport().equalsIgnoreCase(dnew.getUserReport())){
                entity.setReportCount(entity.getReportCount()+1);
                DisasterRepo.save(entity);
                calculateFinalVal(entity.getDisasterId());
                dnew.setStatus(Status.ALREADY_PRESENT);
                dnew.setAssignmentStatus(Assignment.ASSIGNED);
                dnew.setAssignedAdminId(entity.getAssignedAdminId());
                dnew.setLinkedDisasterId(entity.getDisasterId());
                DisasterRepo.save(dnew);
                duplicateDisasterDto dto = new duplicateDisasterDto();
                dto.setDisasterId(entity.getDisasterId());
                dto.setReportCount(entity.getReportCount());
                dto.setFinalConfidence(entity.getFinalConfidence());
                template.convertAndSend("/topic/disaster/"+dnew.getAssignedAdminId(),dto);
                return true;

            }
        }
        return false;
    }

    @Override
    public ResponseEntity<List<disasterDto>> disasterList(ObjectId userId){
        List<disasterEntity> disasters = DisasterRepo.findByAssignedAdminIdAndStatus(userId,Status.UNDER_REVIEW);
        List<disasterDto> dtos = new ArrayList<>();
        for(disasterEntity entity : disasters){
            disasterDto dto = new disasterDto();
            dto.setAiConfidence(entity.getAiConfidence());
            dto.setAiStatus(entity.getAiStatus());
            dto.setDisasterType(entity.getDisasterType());
            dto.setFinalConfidence(entity.getFinalConfidence());
            dto.setForces(entity.getForces());
            dto.setSeverity(entity.getSeverity());
            dto.setReportCount(entity.getReportCount());
            dto.setState(entity.getState());
            dto.setAssignmentStatus(entity.getAssignmentStatus());
            dto.setSuspicious(entity.getSuspicious());
            dto.setImage(entity.getImage());
            dto.setDisasterId(entity.getDisasterId());
            dtos.add(dto);
        }
        return ResponseEntity.ok(dtos);
    }

    
    @Override
    @Transactional
    public ResponseEntity<?> disasterApprove(ObjectId disasterId){
        disasterEntity disaster = DisasterRepo.findByDisasterId(disasterId);
        userEntity user = userrepo.findByUserId(disaster.getUserId());
        Double conf = disaster.getFinalConfidence();
        Double multiplier = 0.0;
        Set<Forces> forces = disaster.getForces();
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
        disaster.setStatus(Status.VERIFIED);
        DisasterRepo.save(disaster);
        template.convertAndSendToUser(user.getUsername(), "/queue/report", new reportResponse(disaster.getDisasterId(),"Disaster is verified.",disaster.getStatus()));
        return ResponseEntity.ok(new reportResponse(disasterId,"Disaster has been verified",disaster.getStatus()));
    }
    
    @Override
    @Transactional
    public ResponseEntity<?> disasterReject(ObjectId disasterId){
        disasterEntity disaster = DisasterRepo.findByDisasterId(disasterId);
        if(disaster==null){
            return ResponseEntity.notFound().build();
        }
        disaster.setStatus(Status.REJECTED);
        DisasterRepo.save(disaster);
        userEntity user = userrepo.findByUserId(disaster.getUserId());
        user.setTrustScore(Math.max(0,user.getTrustScore()-20));
        userrepo.save(user);
        userEntity admin = userrepo.findByUserId(disaster.getAssignedAdminId());
        admin.setActiveIncidents(user.getActiveIncidents()-1);
        userrepo.save(admin);
        template.convertAndSendToUser(user.getUsername(), "/queue/report", new reportResponse(disaster.getDisasterId(),
        "Disaster is rejected. Kindly don't spam the management system",disaster.getStatus()));
        return ResponseEntity.ok(new reportResponse(disasterId,"Disaster has been rejected",disaster.getStatus()));   
    }
}
