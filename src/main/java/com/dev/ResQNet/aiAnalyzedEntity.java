package com.dev.ResQNet;

import java.util.HashSet;
import java.util.Set;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class aiAnalyzedEntity {
    
    private Severity severity;
    private Integer aiConfidence;
    private Set<Disaster> disasterType = new HashSet<>();
    private Set<Forces> forces = new HashSet<>();

}
