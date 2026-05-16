package com.team21.uber.contracts.dto;

import java.time.LocalDateTime;

public record LocationDTO(
        Long id,
        Long driverId,
        Double latitude,
        Double longitude,
        LocalDateTime timestamp
) {}