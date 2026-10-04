package com.dev.ResQNet;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
public class stationDashboard {
    
    private Stats stats;
    private List<dispatchDto> dispatches;
}
