package com.dev.ResQNet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor 
public class Stats {

    private Integer totalAssigned = 0;
    private Integer completed = 0;
    private Integer markedFalse = 0;
    private Integer falseReports = 0; // as total rejected for station dashboard

}
