package com.dev.ResQNet;

import lombok.*;
import java.util.List;

@Getter 
@Setter
@AllArgsConstructor
@NoArgsConstructor 
public class dispatches {
    
    private List<dispatchDto> dispatch;
    private List<dtoBackup> backup;
}
