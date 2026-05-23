package com.team21.uber.ride.dto;

public class RideSummaryDTO {

    private Long userId;
    private long totalRides;
    private long completedRides;
    private long cancelledRides;
    private double totalSpent;
    private double averageFare;

    // ── Builder ──
    private RideSummaryDTO() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final RideSummaryDTO dto = new RideSummaryDTO();

        public Builder userId(Long userId) {
            dto.userId = userId;
            return this;
        }

        public Builder totalRides(long totalRides) {
            dto.totalRides = totalRides;
            return this;
        }

        public Builder completedRides(long completedRides) {
            dto.completedRides = completedRides;
            return this;
        }

        public Builder cancelledRides(long cancelledRides) {
            dto.cancelledRides = cancelledRides;
            return this;
        }

        public Builder totalSpent(double totalSpent) {
            dto.totalSpent = totalSpent;
            return this;
        }

        public Builder averageFare(double averageFare) {
            dto.averageFare = averageFare;
            return this;
        }

        public RideSummaryDTO build() {
            return dto;
        }
    }

    // ── Getters ──

    public Long getUserId() {
        return userId;
    }

    public long getTotalRides() {
        return totalRides;
    }

    public long getCompletedRides() {
        return completedRides;
    }

    public long getCancelledRides() {
        return cancelledRides;
    }

    public double getTotalSpent() {
        return totalSpent;
    }

    public double getAverageFare() {
        return averageFare;
    }

    // ── Static empty factory ──
    public static RideSummaryDTO empty(Long userId) {
        return builder()
                .userId(userId)
                .totalRides(0)
                .completedRides(0)
                .cancelledRides(0)
                .totalSpent(0.0)
                .averageFare(0.0)
                .build();
    }
}