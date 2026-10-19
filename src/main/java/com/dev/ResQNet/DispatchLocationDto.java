package com.dev.ResQNet;

import java.time.LocalDateTime;

public record DispatchLocationDto(
        String dispatchId,
        String disasterId,
        String workerId,
        Double latitude,
        Double longitude,
        Integer routeIndex,
        Integer totalRoutePoints,
        String status,
        boolean arrived,
        LocalDateTime updatedAt
) {}