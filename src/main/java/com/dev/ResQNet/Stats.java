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

    private Integer totalAssigned;
    private Integer completed ;
    private Integer markedFalse;
    private Integer falseReports; // as total rejected for station dashboard

}
