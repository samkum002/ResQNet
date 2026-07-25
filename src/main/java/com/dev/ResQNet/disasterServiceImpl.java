package com.dev.ResQNet;


import java.io.IOException;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
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


    @Override
    @Scheduled(cron = "0 */3 * * * ?")
    public void failedAi() throws IllegalStateException, IOException {

        List<disasterEntity> aiFailed = disasterrepo.findByAiStatusAndRetryCountLessThan(AI.FAILED,5);

        for(disasterEntity de : aiFailed){
            ObjectId imageId = de.getDisasterId();
            aiAnalyzerService.getFile(imageId);
        }
    }
}