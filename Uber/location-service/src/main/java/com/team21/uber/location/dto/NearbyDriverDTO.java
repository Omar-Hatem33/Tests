package com.team21.uber.location.dto;

public class NearbyDriverDTO {

    private Long driverId;
    private String driverName;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;

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

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long driverId;
        private String driverName;
        private Double latitude;
        private Double longitude;
        private Double distanceKm;

        public Builder driverId(Long v) { this.driverId = v; return this; }
        public Builder driverName(String v) { this.driverName = v; return this; }
        public Builder latitude(Double v) { this.latitude = v; return this; }
        public Builder longitude(Double v) { this.longitude = v; return this; }
        public Builder distanceKm(Double v) { this.distanceKm = v; return this; }

        public NearbyDriverDTO build() {
            NearbyDriverDTO dto = new NearbyDriverDTO();
            dto.setDriverId(driverId);
            dto.setDriverName(driverName);
            dto.setLatitude(latitude);
            dto.setLongitude(longitude);
            dto.setDistanceKm(distanceKm);
            return dto;
        }
    }
}
