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
            dto.setAiConfidence(entity.getAiConfidence());
            dto.setAiStatus(entity.getAiStatus());
            dto.setAssignmentStatus(entity.getAssignmentStatus());
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
                    template.convertAndSend("/topic/Disaster/"+admin.getUserId(), dto);
                }
            }
            entity.setAssignmentStatus(Assignment.REASSIGNED);
            DisasterRepo.save(entity);
        }
    }

    @Override
    public void calculateFinalVal(ObjectId disasterId){
        disasterEntity disaster = DisasterRepo.findByDisasterId(disasterId);
        userEntity user = userrepo.findByUserId(disaster.getUserId());
        Double trust = 0.2*user.getTrustScore();
        Double finalVal = ((0.5)*disaster.getAiConfidence()+trust+disaster.getReportCount()*0.3);
        disaster.setFinalConfidence(finalVal);
        DisasterRepo.save(disaster);
    }

    @Override
    public boolean checkDuplicateDisasters(double latitude,double longitude,String state,ObjectId disasterId){
        Query query = new Query();
        query.addCriteria(Criteria.where("state").is(state));
        query.addCriteria(Criteria.where("status").in(Status.UNDER_REVIEW,Status.AI_PROGRESS,Status.BACKUP_DISPATCHED));
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
        Double conf = disaster.getFinalConfidence();
        Set<Forces> forces = disaster.getForces();
        

        return ResponseEntity.ok(new reportResponse(disasterId,"Disaster has been verified",disaster.getStatus()));
    }
    
}
