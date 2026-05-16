package com.team21.uber.ride.dto;

public record DriverRideSummaryDTO(
        long totalRides,
        double totalEarnings,
        double averageFare
) {}
