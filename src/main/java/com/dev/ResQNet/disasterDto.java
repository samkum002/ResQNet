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
    private Severity severity;
    private Integer aiConfidence;
    private Double finalConfidence;
    private Boolean suspicious;
    private String state;
    private Integer reportCount;
    private Assignment assignmentStatus;
    private Set<Disaster> disasterType = new HashSet<>();
    private Set<Forces> forces = new HashSet<>();
    private ObjectId image;
    
}
