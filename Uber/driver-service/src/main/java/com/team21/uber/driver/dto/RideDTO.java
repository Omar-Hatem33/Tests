package com.team21.uber.driver.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal projection of a Ride returned by ride-service via Feign.
 * Only the fields driver-service needs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RideDTO {

    private Long id;
    private Long userId;
    private Long driverId;
    private String status;
    private Double fare;

    public RideDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getFare() { return fare; }
    public void setFare(Double fare) { this.fare = fare; }
}
