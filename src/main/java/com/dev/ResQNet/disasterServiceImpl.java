package com.dev.ResQNet;


import java.io.IOException;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class disasterServiceImpl implements disasterService{
    
    @Autowired
    private disasterRepo disasterrepo;

    @Autowired
    private aiAnalyzerService aiAnalyzerService;

    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Autowired
    SimpMessagingTemplate template;

    @Autowired
    adminDashboardRepo dashboardRepo;


    @Override
    @Scheduled(cron = "0 */3 * * * ?")
    public void failedAi() throws IllegalStateException, IOException {

        List<disasterEntity> aiFailed = disasterrepo.findByAiStatusAndRetryCountLessThan(AI.FAILED,5);

        for(disasterEntity de : aiFailed){
            ObjectId imageId = de.getDisasterId();
            aiAnalyzerService.getFile(imageId);
        }
    }

    @Override
    @Scheduled(cron = "0 */1 * * * ?")
    public void handleFailedAi() throws IllegalStateException, IOException {

        List<disasterEntity> aiFailed = disasterrepo.findByAiStatusAndRetryCount(AI.FAILED,5);

        for(disasterEntity de : aiFailed){
            de.setAiStatus(AI.MANUAL_REVIEW);
            disasterrepo.save(de);
            List<userEntity> admins = dashboardRepo.findByRolesContainingAndAdminState("ADMIN", de.getState());
            List<userEntity> filteredAdmins = admins.stream().filter(admin->admin.getAdminStatus()!=Admin.OFFLINE).toList();
            for(userEntity a : filteredAdmins){
                template.convertAndSend("/topic/Disaster/"+a.getUserId(), de);
            }
        }
    }


     
}