package com.dev.ResQNet;

import org.bson.types.ObjectId;

public interface stationService {
    
    void findStation(ObjectId disasterId, Integer newTrucks, Integer newPersonnel);
}
