package com.dev.ResQNet;

import java.io.IOException;

import org.bson.types.ObjectId;

public interface  aiAnalyzerService {
    
    void getFile(ObjectId id) throws IllegalStateException, IOException;
}
