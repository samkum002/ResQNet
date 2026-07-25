package com.dev.ResQNet;

import java.io.IOException;

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.mongodb.client.gridfs.model.GridFSFile;

@Service
public class aiAnalyzerServiceImpl implements aiAnalyzerService {
    
    @Autowired
    GridFsTemplate gridFsTemplate;

    @Autowired
    disasterService disasterService;

    
    @Async
    @Override
    public void getFile(ObjectId id) throws IllegalStateException, IOException{

        GridFSFile gfs = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(id)));
        if(gfs == null){
            throw new IllegalStateException("File not found");
        }
        disasterService.aiAnalyzer(gridFsTemplate.getResource(gfs).getInputStream().readAllBytes());
    }

}
