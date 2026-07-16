package com.dev.ResQNet;

import org.bson.types.ObjectId;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class reportResponse {
    
    public ObjectId disasterId;
    public String msg;
    public Status status;

}
