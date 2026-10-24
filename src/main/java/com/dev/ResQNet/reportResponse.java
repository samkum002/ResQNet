package com.dev.ResQNet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class reportResponse {
    
    public String disasterId;
    public String msg;
    public Status status;

}
