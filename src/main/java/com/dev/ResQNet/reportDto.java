package com.dev.ResQNet;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class reportDto {

    @NotBlank(message="Please Enter valid Disaster Cause.")
    private String userReport;
}
