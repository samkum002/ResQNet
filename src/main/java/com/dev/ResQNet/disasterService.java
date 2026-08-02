package com.dev.ResQNet;

import java.io.IOException;

import org.bson.types.ObjectId;

public interface disasterService {
    
    void failedAi() throws IllegalStateException, IOException;
    void handleFailedAi() throws IllegalStateException, IOException;
    
}
