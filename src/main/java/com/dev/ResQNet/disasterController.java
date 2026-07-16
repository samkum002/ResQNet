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

@RestController
@RequestMapping("/disaster")
public class disasterController {

    @Autowired
    GridFsTemplate gridFsTemplate;

    @Autowired
    disasterRepo disasterrepo;

    @Autowired
    userRepo userrepo;
    
    @PostMapping("/image-upload")
    public ResponseEntity<?> imageUpload(@RequestParam("image") MultipartFile image,@RequestParam("state") String state,@RequestParam("longitude") Double longitude,@RequestParam("latitude") Double latitude) throws IOException{

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth.getName();
        userEntity user = userrepo.findByUsername(name);
        if(user==null){
            return ResponseEntity.badRequest().body("user doesn't exist");
        }
        ObjectId userId = user.getUserId();
        disasterEntity disaster = new disasterEntity();
        disaster.setUserId(userId);
        disaster.setStatus(Status.REPORTED);
        disaster.setCreatedAt(LocalDateTime.now());
        disaster.setRetryCount(0);
        disaster.setState(state);
        disaster.setLocation(new GeoJsonPoint(longitude,latitude));
        disaster.setReAssigned(false);
        byte[] image_bytes = image.getBytes();
        InputStream image_to_stream = new ByteArrayInputStream(image_bytes);
        ObjectId image_store = gridFsTemplate.store(image_to_stream,image.getOriginalFilename(),image.getContentType());
        disaster.setImage(image_store);
        disasterrepo.save(disaster);
        return ResponseEntity.ok(new reportResponse(disaster.getDisasterId(),"Reported Successfully",disaster.getStatus()));
    }
}
