package com.team21.uber.ride.dto;

/**
 * Returned by GET /api/rides/driver/{driverId}/summary
 * Called by driver-service (S2-F3, S2-F12) via Feign.
 */
public class DriverRideSummaryDTO {

    private Long driverId;
    private long totalRides;
    private double totalEarnings;
    private double averageFare;

    private DriverRideSummaryDTO() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final DriverRideSummaryDTO dto = new DriverRideSummaryDTO();

        public Builder driverId(Long driverId) {
            dto.driverId = driverId;
            return this;
        }

        public Builder totalRides(long totalRides) {
            dto.totalRides = totalRides;
            return this;
        }

        public Builder totalEarnings(double totalEarnings) {
            dto.totalEarnings = totalEarnings;
            return this;
        }

        public Builder averageFare(double averageFare) {
            dto.averageFare = averageFare;
            return this;
        }

        public DriverRideSummaryDTO build() {
            return dto;
        }
    }

    public Long getDriverId() {
        return driverId;
    }

    public long getTotalRides() {
        return totalRides;
    }

    public double getTotalEarnings() {
        return totalEarnings;
    }

    public double getAverageFare() {
        return averageFare;
    }

    public static DriverRideSummaryDTO empty(Long driverId) {
        return builder()
                .driverId(driverId)
                .totalRides(0)
                .totalEarnings(0.0)
                .averageFare(0.0)
                .build();
    }
}