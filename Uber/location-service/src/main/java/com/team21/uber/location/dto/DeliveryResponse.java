package com.team21.uber.location.dto;

import com.team21.uber.location.model.Location;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class DeliveryResponse {

    private Long id;
    private Long driverId;
    private Double latitude;
    private Double longitude;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;

    public static DeliveryResponse fromEntity(Location delivery) {
        DeliveryResponse response = new DeliveryResponse();
        response.setId(delivery.getId());
        response.setDriverId(delivery.getDriverId());
        response.setLatitude(delivery.getLatitude());
        response.setLongitude(delivery.getLongitude());
        response.setTimestamp(delivery.getTimestamp());
        response.setMetadata(delivery.getMetadata() == null ? new HashMap<>() : new HashMap<>(delivery.getMetadata()));
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
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

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Long id;
        private Long driverId;
        private Double latitude;
        private Double longitude;
        private LocalDateTime timestamp;
        private Map<String, Object> metadata;

        public Builder id(Long v) { this.id = v; return this; }
        public Builder driverId(Long v) { this.driverId = v; return this; }
        public Builder latitude(Double v) { this.latitude = v; return this; }
        public Builder longitude(Double v) { this.longitude = v; return this; }
        public Builder timestamp(LocalDateTime v) { this.timestamp = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }

        public DeliveryResponse build() {
            DeliveryResponse dto = new DeliveryResponse();
            dto.setId(id);
            dto.setDriverId(driverId);
            dto.setLatitude(latitude);
            dto.setLongitude(longitude);
            dto.setTimestamp(timestamp);
            dto.setMetadata(metadata);
            return dto;
        }
    }
}