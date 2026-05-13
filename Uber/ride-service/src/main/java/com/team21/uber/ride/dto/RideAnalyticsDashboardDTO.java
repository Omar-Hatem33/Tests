package com.team21.uber.ride.dto;

import java.util.Map;

public class RideAnalyticsDashboardDTO {

    private long totalRides;
    private double totalRevenue;
    private double averageRideFare;
    private double completionRate;
    private Map<String, Long> ridesByStatus;

    public RideAnalyticsDashboardDTO() {}

    public RideAnalyticsDashboardDTO(long totalRides,
                                     double totalRevenue,
                                     double averageRideFare,
                                     double completionRate,
                                     Map<String, Long> ridesByStatus) {
        this.totalRides = totalRides;
        this.totalRevenue = totalRevenue;
        this.averageRideFare = averageRideFare;
        this.completionRate = completionRate;
        this.ridesByStatus = ridesByStatus;
    }

    public long getTotalRides() { return totalRides; }
    public void setTotalRides(long totalRides) { this.totalRides = totalRides; }

    public double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }

    public double getAverageRideFare() { return averageRideFare; }
    public void setAverageRideFare(double averageRideFare) { this.averageRideFare = averageRideFare; }

    public double getCompletionRate() { return completionRate; }
    public void setCompletionRate(double completionRate) { this.completionRate = completionRate; }

    public Map<String, Long> getRidesByStatus() { return ridesByStatus; }
    public void setRidesByStatus(Map<String, Long> ridesByStatus) { this.ridesByStatus = ridesByStatus; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private long totalRides;
        private double totalRevenue;
        private double averageRideFare;
        private double completionRate;
        private Map<String, Long> ridesByStatus;

        public Builder totalRides(long v) { this.totalRides = v; return this; }
        public Builder totalRevenue(double v) { this.totalRevenue = v; return this; }
        public Builder averageRideFare(double v) { this.averageRideFare = v; return this; }
        public Builder completionRate(double v) { this.completionRate = v; return this; }
        public Builder ridesByStatus(Map<String, Long> v) { this.ridesByStatus = v; return this; }

        public RideAnalyticsDashboardDTO build() {
            return new RideAnalyticsDashboardDTO(totalRides, totalRevenue, averageRideFare, completionRate, ridesByStatus);
        }
    }
}
