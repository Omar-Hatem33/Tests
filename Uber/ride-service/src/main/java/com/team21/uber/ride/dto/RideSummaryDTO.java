package com.team21.uber.ride.dto;

public record RideSummaryDTO(
        long totalRides,
        long completedRides,
        long cancelledRides,
        double totalSpent,
        double averageFare
) {}
