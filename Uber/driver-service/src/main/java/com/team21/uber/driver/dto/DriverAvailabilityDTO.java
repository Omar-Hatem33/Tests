package com.team21.uber.driver.dto;

import com.team21.uber.driver.model.DriverStatus;

/**
 * Response DTO for GET /api/drivers/{id}/availability.
 * Called by ride-service via Feign to check driver status before assignment.
 */
public class DriverAvailabilityDTO {

    private Long driverId;
    private DriverStatus status;
    private boolean available;

    public DriverAvailabilityDTO() {}

    public DriverAvailabilityDTO(Long driverId, DriverStatus status) {
        this.driverId = driverId;
        this.status = status;
        this.available = status == DriverStatus.AVAILABLE;
    }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public DriverStatus getStatus() { return status; }
    public void setStatus(DriverStatus status) { this.status = status; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
