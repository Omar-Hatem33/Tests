package com.team21.uber.driver.dto;

public class DriverEarningsDTO {

     private Long driverId;
    private String driverName;
    private Long totalRides;
    private Double totalEarnings;
    private Double averageEarningsPerRide;
    private Double totalDistance;
    private Double averageRating;
 
    private DriverEarningsDTO() {}
 
    public Long getDriverId()        { return driverId; }
    public String getDriverName()    { return driverName; }
    public Long getTotalRides()      { return totalRides; }
    public Double getTotalEarnings()          { return totalEarnings; }
    public Double getAverageEarningsPerRide() { return averageEarningsPerRide; }
    public Double getTotalDistance()          { return totalDistance; }
    public Double getAverageRating()          { return averageRating; }
 
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final DriverEarningsDTO dto = new DriverEarningsDTO();

        public Builder driverId(Long driverId) {
            dto.driverId = driverId;
            return this;
        }
 
        public Builder driverName(String driverName) {
            dto.driverName = driverName;
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
 
        public Builder averageEarningsPerRide(Double averageEarningsPerRide) {
            dto.averageEarningsPerRide = averageEarningsPerRide;
            return this;
        }
 
        public Builder totalDistance(Double totalDistance) {
            dto.totalDistance = totalDistance;
            return this;
        }
 
        public Builder averageRating(Double averageRating) {
            dto.averageRating = averageRating;
            return this;
        }
 
        public DriverEarningsDTO build() {
            return dto;
        }
    }
}
