package com.team21.uber.contracts.dto;

import java.util.Map;

public record DriverDTO(
        Long id,
        String name,
        String phone,
        String status,
        Double rating,
        Integer totalRatings,
        Map<String, Object> vehicleDetails
) {}