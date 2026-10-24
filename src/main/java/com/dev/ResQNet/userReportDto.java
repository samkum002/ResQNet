package com.dev.ResQNet;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class userReportDto {
    private String disasterId;
    private String msg;
    private Status status;
    private String cause;
    private Double latitude;
    private Double longitude;
    private LocalDateTime createdAt;
}
