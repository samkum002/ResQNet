package com.dev.ResQNet;

import java.util.List;
import lombok.*;

@Getter 
@Setter
@AllArgsConstructor

public class adminDashboard {
    
    private Stats stats;
    private List<disasterDto> disasters;
}
