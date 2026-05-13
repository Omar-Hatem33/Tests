package com.team21.uber.contracts.dto;

public record RideSummaryDTO(
        long totalRides,
        long completedRides,
        long cancelledRides,
        double totalSpent,
        double averageFare
) {
    public static RideSummaryDTO empty() {
        return new RideSummaryDTO(0L, 0L, 0L, 0.0, 0.0);
    }
}