package com.team21.uber.location.dto;

import java.time.LocalDateTime;

public class DriverMovementSummaryDTO {

    private Long driverId;
    private String driverName;
    private long totalLocationPoints;
    private double averageSpeed;
    private double maxSpeed;
    private LocalDateTime firstTimestamp;
    private LocalDateTime lastTimestamp;

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(String driverName) {
        this.driverName = driverName;
    }

    public long getTotalLocationPoints() {
        return totalLocationPoints;
    }

    public void setTotalLocationPoints(long totalLocationPoints) {
        this.totalLocationPoints = totalLocationPoints;
    }

    public double getAverageSpeed() {
        return averageSpeed;
    }

    public void setAverageSpeed(double averageSpeed) {
        this.averageSpeed = averageSpeed;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public LocalDateTime getFirstTimestamp() {
        return firstTimestamp;
    }

    public void setFirstTimestamp(LocalDateTime firstTimestamp) {
        this.firstTimestamp = firstTimestamp;
    }

    public LocalDateTime getLastTimestamp() {
        return lastTimestamp;
    }

    public void setLastTimestamp(LocalDateTime lastTimestamp) {
        this.lastTimestamp = lastTimestamp;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long driverId;
        private String driverName;
        private long totalLocationPoints;
        private double averageSpeed;
        private double maxSpeed;
        private LocalDateTime firstTimestamp;
        private LocalDateTime lastTimestamp;

        public Builder driverId(Long v) { this.driverId = v; return this; }
        public Builder driverName(String v) { this.driverName = v; return this; }
        public Builder totalLocationPoints(long v) { this.totalLocationPoints = v; return this; }
        public Builder averageSpeed(double v) { this.averageSpeed = v; return this; }
        public Builder maxSpeed(double v) { this.maxSpeed = v; return this; }
        public Builder firstTimestamp(LocalDateTime v) { this.firstTimestamp = v; return this; }
        public Builder lastTimestamp(LocalDateTime v) { this.lastTimestamp = v; return this; }

        public DriverMovementSummaryDTO build() {
            DriverMovementSummaryDTO dto = new DriverMovementSummaryDTO();
            dto.setDriverId(driverId);
            dto.setDriverName(driverName);
            dto.setTotalLocationPoints(totalLocationPoints);
            dto.setAverageSpeed(averageSpeed);
            dto.setMaxSpeed(maxSpeed);
            dto.setFirstTimestamp(firstTimestamp);
            dto.setLastTimestamp(lastTimestamp);
            return dto;
        }
    }
}
