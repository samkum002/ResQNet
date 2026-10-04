package com.dev.ResQNet;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;
// import org.bson.types.ObjectId;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class dispatchDto {
    
    private Severity severity;
    private String dispatchId;
    private Forces forceType;
    private Integer assignedVehicle;
    private Integer assignedPersonnel;
    private Status status;
}
