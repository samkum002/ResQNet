package com.dev.ResQNet;

import java.io.IOException;

import org.bson.types.ObjectId;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import com.mongodb.client.gridfs.model.GridFSFile;

@Service
public class aiAnalyzerServiceImpl implements aiAnalyzerService {
    
    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Autowired
    private disasterRepo disasterrepo;

    private final ChatClient chatClient;

    public aiAnalyzerServiceImpl(ChatClient.Builder builder){
        this.chatClient = builder.build();
    }

    
    @Async
    @Override
    public void getFile(ObjectId id) throws IllegalStateException, IOException{

        ObjectId imageId = disasterrepo.findImageByDisasterId(id);
        GridFSFile gfs = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(imageId)));
        if(gfs == null){
            throw new IllegalStateException("File not found");
        }
        aiAnalyzer(gridFsTemplate.getResource(gfs).getInputStream().readAllBytes());
    }

    @Async
    @Override
    public void aiAnalyzer(byte[] image_bytes){

        Media m = new Media(MimeTypeUtils.IMAGE_JPEG,new ByteArrayResource(image_bytes));

        try{
            aiAnalyzedEntity aiEntity = chatClient.prompt().system("""
            You are an AI disaster assessment expert.
            Analyze the provided disaster image carefully.
            Return ONLY valid JSON matching the AIAnalyzedEntity schema.
            Do not include markdown, explanations, or additional text.

            Rules:

            1. confidence
            - Return an integer between 1 and 100.
            - The value should represent how confident you are that a real disaster is present in the image.
            - 100 means extremely confident.
            - 1 means almost certainly not a disaster.

            2. severity
            - Return ONLY one of:
                LOW
                MEDIUM
                HIGH
                CRITICAL

            3. disasterTypes
            - Return a JSON array.
            - Use ONLY these enum values:
                FIRE
                LANDSLIDE
                EARTHQUAKE
                BUILDING_COLLAPSE
                ROAD_ACCIDENT
                ELECTRICAL_HAZARD

            4. requiredResponseForces
            - Return a JSON array.
            - Use ONLY these enum values:
                FIRE_DEPARTMENT
                POLICE_STATION
                AMBULANCE
                SDRF
                NDRF
                ELECTRICITY_DEPARTMENT
            """)
            .user(u->u.text("Analyze the uploaded image thoroughly").media(m)).call()
            .entity(aiAnalyzedEntity.class);

            disasterEntity disasterentity = new disasterEntity();
            disasterentity.setAiStatus(AI.COMPLETED);
            disasterentity.setAiConfidence(aiEntity.getAiConfidence());
            disasterentity.setSeverity(aiEntity.getSeverity());
            disasterentity.setForces(aiEntity.getForces());
            disasterentity.setDisasterType(aiEntity.getDisasterType());
            disasterrepo.save(disasterentity);
        }
        catch(Exception e){
            disasterEntity disasterentity = new disasterEntity();
            disasterentity.setRetryCount(disasterentity.getRetryCount()+1);
            disasterentity.setAiStatus(AI.FAILED);
            disasterrepo.save(disasterentity);
        }

    }

}
