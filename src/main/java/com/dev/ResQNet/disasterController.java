package com.dev.ResQNet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/disaster")
public class disasterController {

    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Autowired
    private adminDashboardService dashboardService;

    @Autowired
    private disasterRepo disasterrepo;

    @Autowired
    private aiAnalyzerService aiAnalyzerservice;

    @Autowired
    private disasterService disasterservice;

    @Autowired
    private userRepo userrepo;

    @PostMapping("/report")
    public ResponseEntity<?> imageUpload(@RequestParam("image") MultipartFile image,@RequestParam("state") String state,@RequestParam("longitude") Double longitude,@RequestParam("latitude") Double latitude,@NotBlank(message="Please Enter valid Disaster Cause in one word only.")@Pattern(regexp="^[A-Za-z]+$")@RequestParam("userReport") reportDto dto) throws IOException{

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        userEntity user = userrepo.findByUsername(name);
        if(user==null){
            return ResponseEntity.badRequest().body("user doesn't exist");
        }
        ObjectId userId = user.getUserId();
        disasterEntity disaster = new disasterEntity();
        disaster.setUserReport(dto.getUserReport());
        disaster.setUserId(userId);
        disaster.setStatus(Status.REPORTED);
        disaster.setCreatedAt(LocalDateTime.now());
        disaster.setReportCount(1);
        disaster.setRetryCount(0);
        disaster.setAiConfidence(0);
        disaster.setFinalConfidence(0.0);
        disaster.setState(state);
        disaster.setLocation(new GeoJsonPoint(longitude,latitude));
        disaster.setAssignmentStatus(Assignment.CREATED);
        byte[] image_bytes = image.getBytes();
        InputStream image_to_stream = new ByteArrayInputStream(image_bytes);
        String content = image.getContentType();
        ObjectId image_store = gridFsTemplate.store(image_to_stream,image.getOriginalFilename(),content);
        disaster.setImage(image_store);
        disasterrepo.save(disaster);
        ObjectId disasterId = disaster.getDisasterId();
        boolean isDuplicate = dashboardService.checkDuplicateDisasters(latitude, longitude,disaster.getState(),disasterId);
        if(!isDuplicate){
            aiAnalyzerservice.aiAnalyzer(image_bytes,disasterId,content);
            disaster.setAiStatus(AI.PROCESSING);
        }
        return ResponseEntity.ok(new reportResponse(disaster.getDisasterId(),"Reported Successfully",disaster.getStatus()));
    }
}
