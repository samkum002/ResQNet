package com.dev.ResQNet;

import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class duplicateDisasterDto {
    
    public ObjectId disasterId;
    public Integer reportCount;
    public Double finalConfidence;
}
