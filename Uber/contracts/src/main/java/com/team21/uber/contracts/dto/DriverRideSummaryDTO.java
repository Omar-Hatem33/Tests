package com.team21.uber.contracts.dto;

public record DriverRideSummaryDTO(
        long totalRides,
        double totalEarnings,
        double averageFare
) {
    public static DriverRideSummaryDTO empty() {
        return new DriverRideSummaryDTO(0L, 0.0, 0.0);
    }
}