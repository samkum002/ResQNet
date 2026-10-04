package com.dev.ResQNet;

import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class dtoBackup {
    
    private Severity severity;
    private String backupId;
    private Forces forceType;
    private Integer assignedVehicle;
    private Integer assignedPersonnel;
    private backupStatus status;
}
