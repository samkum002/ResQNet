package com.dev.ResQNet;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    private Boolean arrived = false;

    public dispatchDto(Severity severity, String dispatchId, Forces forceType, Integer assignedVehicle, Integer assignedPersonnel, Status status) {
        this.severity = severity;
        this.dispatchId = dispatchId;
        this.forceType = forceType;
        this.assignedVehicle = assignedVehicle;
        this.assignedPersonnel = assignedPersonnel;
        this.status = status;
        this.arrived = false;
    }
}
