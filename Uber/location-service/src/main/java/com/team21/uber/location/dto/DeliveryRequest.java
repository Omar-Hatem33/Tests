package com.team21.uber.location.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.Map;

public class DeliveryRequest {

    @JsonAlias({"lat", "latitude"})
    private Double latitude;

    @JsonAlias({"lon", "longitude"})
    private Double longitude;

    private Map<String, Object> metadata;

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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}