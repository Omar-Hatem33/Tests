package com.team21.uber.driver.dto;

import com.team21.uber.driver.model.DriverStatus;

public class AvailabilityUpdateRequest {

    private DriverStatus status;

    public AvailabilityUpdateRequest() {
    }

    public DriverStatus getStatus() {
        return status;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }
}