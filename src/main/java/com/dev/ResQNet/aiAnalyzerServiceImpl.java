package com.dev.ResQNet;

import java.io.IOException;

import org.bson.types.ObjectId;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.gridfs.model.GridFSFile;

@Service
public class aiAnalyzerServiceImpl implements aiAnalyzerService {
    
    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Autowired
    private adminDashboardService dashboardService;

    @Autowired
    private disasterRepo disasterrepo;

    @Autowired
    private userRepo userrepo;

    private final ChatClient chatClient;

    public aiAnalyzerServiceImpl(ChatClient.Builder builder){
        this.chatClient = builder.build();
    }

    
    @Async
    @Override
    public void getFile(ObjectId id) throws IllegalStateException, IOException{

        disasterEntity disasterentity = disasterrepo.findByDisasterId(id);
        ObjectId imageId = disasterentity.getImage();
        GridFSFile gfs = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(imageId)));
        if(gfs == null){
            throw new IllegalStateException("File not found");
        }

        GridFsResource resource = gridFsTemplate.getResource(gfs);
        aiAnalyzer(resource.getInputStream().readAllBytes(),id,resource.getContentType());
    }

    @Async
    @Override
    public void aiAnalyzer(byte[] image_bytes,ObjectId disasterId,String content){

        Media m = new Media(MimeTypeUtils.parseMimeType(content),new ByteArrayResource(image_bytes));
        // System.out.println("Analyzing image for disasterId: " + disasterId);

        try{
            String rawResponse = chatClient.prompt().system("""
            You are an AI disaster assessment expert.
            Analyze the provided disaster image carefully.
            Return ONLY valid JSON matching the AIAnalyzedEntity schema.
            Do not include markdown, explanations, or additional text.
            Do not wrap it in ''' json,
            Do not say 'Here is the JSON',
            Do not include explanations.
            The first character of your response must be '{'.
            The last character of your response must be '}'.
            The JSON must match the aiAnalyzedEntity schema.

            Rules:

            1. aiConfidence
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

            3. disasterType
            - Return a JSON array.
            - Use ONLY these enum values:
                FIRE
                LANDSLIDE
                EARTHQUAKE
                BUILDING_COLLAPSE
                ROAD_ACCIDENT
                ELECTRICAL_HAZARD

            4. Forces 
            - Prioritize forces according to the immediate emergency response needs visible or reasonably inferable from the image.

                1. The force directly responsible for controlling the primary visible
                disaster should receive the highest priority.
                2. Give high priority to medical/ambulance response when the incident
                could reasonably involve injured or trapped people.
                3. Give police priority when crowd control, evacuation, traffic control,
                or securing the scene is relevant.
                4. Give electricity department priority only when an electrical hazard,
                electrical fire, exposed wiring, transformers, or similar evidence
                is visible or reasonably inferable.
                5. Give NDRF/SDRF priority only when the image provides evidence that
                specialized disaster rescue is required.
                
            - Do not rank a force highly merely because it is potentially useful.
            - Prioritize based on the immediate needs of this specific incident.
            - Return a JSON array.
            - Use ONLY these enum values:
                FIRE_DEPARTMENT
                POLICE_STATION
                AMBULANCE
                SDRF
                NDRF
                ELECTRICITY_DEPARTMENT

            - Return ONLY the specified JSON fields.
            - Do not include reasoningSummary or any other fields.
            """)
            .user(u->u.text("Analyze the uploaded image thoroughly").media(m)).call().content();
            
            // .entity(aiAnalyzedEntity.class,spec -> spec.useProviderStructuredOutput().validateSchema());
            // System.out.println("Raw AI Response: " + rawResponse);

            int jsonStart = rawResponse.indexOf("{");
            int jsonEnd = rawResponse.lastIndexOf("}");
            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                String cleanJson = rawResponse.substring(jsonStart, jsonEnd + 1);
                ObjectMapper objectMapper = new ObjectMapper();
                aiAnalyzedEntity aiEntity = objectMapper.readValue(cleanJson, aiAnalyzedEntity.class);
                disasterEntity disasterentity = disasterrepo.findByDisasterId(disasterId);
                disasterentity.setStatus(Status.AI_PROGRESS);
                disasterentity.setAiStatus(AI.COMPLETED);
                disasterentity.setAiConfidence(aiEntity.getAiConfidence());
                disasterentity.setSeverity(aiEntity.getSeverity());
                disasterentity.setForces(aiEntity.getForces());
                disasterentity.setDisasterType(aiEntity.getDisasterType());
                disasterrepo.save(disasterentity);
            }
        }
        catch(Exception e){
            disasterEntity disasterentity = disasterrepo.findByDisasterId(disasterId);
            disasterentity.setRetryCount(disasterentity.getRetryCount()+1);
            disasterentity.setAiStatus(AI.FAILED);
            disasterrepo.save(disasterentity);
            e.printStackTrace();
            return;
        }
        dashboardService.calculateFinalVal(disasterId);
        dashboardService.checkInfo(disasterId);

    }

}





