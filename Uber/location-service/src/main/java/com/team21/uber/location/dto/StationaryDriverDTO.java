package com.team21.uber.location.dto;

import java.time.LocalDateTime;

public class StationaryDriverDTO {

    private Long driverId;
    private String driverName;
    private Double latitude;
    private Double longitude;
    private Double lastSpeed;
    private LocalDateTime lastUpdated;

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

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLastSpeed() {
        return lastSpeed;
    }

    public void setLastSpeed(Double lastSpeed) {
        this.lastSpeed = lastSpeed;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long driverId;
        private String driverName;
        private Double latitude;
        private Double longitude;
        private Double lastSpeed;
        private LocalDateTime lastUpdated;

        public Builder driverId(Long v) { this.driverId = v; return this; }
        public Builder driverName(String v) { this.driverName = v; return this; }
        public Builder latitude(Double v) { this.latitude = v; return this; }
        public Builder longitude(Double v) { this.longitude = v; return this; }
        public Builder lastSpeed(Double v) { this.lastSpeed = v; return this; }
        public Builder lastUpdated(LocalDateTime v) { this.lastUpdated = v; return this; }

        public StationaryDriverDTO build() {
            StationaryDriverDTO dto = new StationaryDriverDTO();
            dto.setDriverId(driverId);
            dto.setDriverName(driverName);
            dto.setLatitude(latitude);
            dto.setLongitude(longitude);
            dto.setLastSpeed(lastSpeed);
            dto.setLastUpdated(lastUpdated);
            return dto;
        }
    }
}
