package com.team21.uber.driver.dto;

public class DriverDashboardDTO {

    private Long driverId;
    private String name;
    private Long totalRides;
    private Double totalEarnings;
    private Double averageRideFare;
    private Double averageRating;
    private Integer totalRatings;

    private DriverDashboardDTO() {}

    public Long getDriverId()        { return driverId; }
    public String getName()          { return name; }
    public Long getTotalRides()      { return totalRides; }
    public Double getTotalEarnings() { return totalEarnings; }
    public Double getAverageRideFare() { return averageRideFare; }
    public Double getAverageRating() { return averageRating; }
    public Integer getTotalRatings() { return totalRatings; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final DriverDashboardDTO dto = new DriverDashboardDTO();

        public Builder driverId(Long driverId) {
            dto.driverId = driverId;
            return this;
        }

        public Builder name(String name) {
            dto.name = name;
            return this;
        }

        public Builder totalRides(Long totalRides) {
            dto.totalRides = totalRides;
            return this;
        }

        public Builder totalEarnings(Double totalEarnings) {
            dto.totalEarnings = totalEarnings;
            return this;
        }

        public Builder averageRideFare(Double averageRideFare) {
            dto.averageRideFare = averageRideFare;
            return this;
        }

        public Builder averageRating(Double averageRating) {
            dto.averageRating = averageRating;
            return this;
        }

        public Builder totalRatings(Integer totalRatings) {
            dto.totalRatings = totalRatings;
            return this;
        }

        public DriverDashboardDTO build() {
            return dto;
        }
    }
}