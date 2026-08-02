package com.dev.ResQNet;

import java.io.IOException;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mongodb.client.gridfs.model.GridFSFile;

@RestController
@RequestMapping("/fetch")
public class adminDashboardController {
    
    @Autowired
    disasterController controllerDisaster;

    @Autowired
    GridFsTemplate template;

    @GetMapping("/image/{imageId}")
    public ResponseEntity<byte[]> fetchImage(@PathVariable ObjectId imageId) throws IOException{
        
        GridFSFile gfs = template.findOne(Query.query(Criteria.where("_id").is(imageId)));
        GridFsResource resource = template.getResource(gfs);

        return ResponseEntity.ok().contentType(MediaType.parseMediaType(resource.getContentType())).body(resource.getInputStream().readAllBytes());
    }
}
