package com.dev.ResQNet;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /dispatch/recall/{dispatchId}.
 */
@Getter
@Setter
@NoArgsConstructor
public class recallRequestBody {
    private String reason;
}
