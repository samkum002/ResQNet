package com.dev.ResQNet;

import java.util.HashSet;
import java.util.Set;

import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class disasterDto {

    private AI aiStatus;
    private Status status;
    private Severity severity;
    private Integer aiConfidence;
    private Double finalConfidence;
    private Boolean suspicious;
    private String state;
    private String userReport;
    private Integer reportCount;
    private Assignment assignmentStatus;
    private Set<Disaster> disasterType;
    private Set<Forces> forces;
    private String image;
    private String disasterId;
    // private Stats stats;
    
}
