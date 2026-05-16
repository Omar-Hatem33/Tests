package com.team21.uber.contracts.dto;

public record RideSummaryDTO(
        int totalRides,
        int completedRides,
        int cancelledRides,
        double totalSpent,
        double averageFare
) {
    public static RideSummaryDTO empty() {
        return new RideSummaryDTO(0, 0, 0, 0.0, 0.0);
    }
}