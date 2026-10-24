package com.dev.ResQNet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class stationMapDto {
    private String stationId;
    private String stationName;
    private String forceType;
    private String state;
    private Double latitude;
    private Double longitude;
}
