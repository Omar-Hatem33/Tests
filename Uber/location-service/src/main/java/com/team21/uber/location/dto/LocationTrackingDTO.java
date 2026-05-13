package com.team21.uber.location.dto;

import java.time.Instant;
import java.util.UUID;

public class LocationTrackingDTO {

    private UUID trackingId;
    private String driverId;
    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private Double speed;
    private Double heading;
    private String notes;
    private Instant timestamp;
    private Boolean success;

    public LocationTrackingDTO() {}

    public static Builder builder() { return new Builder(); }

    public UUID getTrackingId() { return trackingId; }
    public void setTrackingId(UUID trackingId) { this.trackingId = trackingId; }

    public String getDriverId() { return driverId; }
    public void setDriverId(String driverId) { this.driverId = driverId; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }

    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }

    public Double getHeading() { return heading; }
    public void setHeading(Double heading) { this.heading = heading; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public static class Builder {
        private final LocationTrackingDTO dto = new LocationTrackingDTO();

        public Builder trackingId(UUID v) { dto.trackingId = v; return this; }
        public Builder driverId(String v) { dto.driverId = v; return this; }
        public Builder latitude(Double v) { dto.latitude = v; return this; }
        public Builder longitude(Double v) { dto.longitude = v; return this; }
        public Builder accuracy(Double v) { dto.accuracy = v; return this; }
        public Builder speed(Double v) { dto.speed = v; return this; }
        public Builder heading(Double v) { dto.heading = v; return this; }
        public Builder notes(String v) { dto.notes = v; return this; }
        public Builder timestamp(Instant v) { dto.timestamp = v; return this; }
        public Builder success(Boolean v) { dto.success = v; return this; }

        public LocationTrackingDTO build() { return dto; }
    }
}
