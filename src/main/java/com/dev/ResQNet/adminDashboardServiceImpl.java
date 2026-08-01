package com.dev.ResQNet;

import java.util.Comparator;
import java.util.List;

import org.bson.types.ObjectId;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;

public class adminDashboardServiceImpl implements adminDashboardService{

    @Autowired
    adminDashboardRepo dashboardRepo;

    @Autowired
    SimpMessagingTemplate template;

    @Autowired
    userRepo userrepo;

    @Autowired
    disasterRepo DisasterRepo;
    
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
        entity.setAssignmentStatus(Assignment.PROCESSING);
        List<userEntity> admins = dashboardRepo.findByRolesContainingAndAdminStateAndAdminStatus("ADMIN",entity.getState(),Admin.AVAILABLE);
        if(admins.isEmpty()){
            entity.setAssignmentStatus(Assignment.TIMEOUT);
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
        disasterDto dto = new disasterDto();
        dto.setAiConfidence(entity.getAiConfidence());
        dto.setAiStatus(entity.getAiStatus());
        dto.setAssignmentStatus(entity.getAssignmentStatus());
        dto.setDisasterType(entity.getDisasterType());
        dto.setForces(entity.getForces());
        dto.setFinalConfidence(entity.getFinalConfidence());
        dto.setImage(entity.getImage());
        dto.setState(entity.getState());
        dto.setSeverity(entity.getSeverity());
        dto.setSuspicious(entity.getSuspicious());
        dto.setReportCount(entity.getReportCount());
        if(sortedAdmin.getActiveIncidents()>3){
            sortedAdmin.setAdminStatus(Admin.BUSY);
        }
        userrepo.save(sortedAdmin);
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
}
