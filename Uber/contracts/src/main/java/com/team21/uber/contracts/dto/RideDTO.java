package com.team21.uber.contracts.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record RideDTO(
        Long id,
        Long userId,
        Long driverId,
        String status,
        Double fare,
        LocalDateTime requestedAt,
        LocalDateTime completedAt,
        Map<String, Object> metadata
) {}