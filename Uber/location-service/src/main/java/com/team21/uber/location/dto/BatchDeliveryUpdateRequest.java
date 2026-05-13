package com.team21.uber.location.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchDeliveryUpdateRequest {

    private Long driverId;

    private List<DeliveryRequest> locations = new ArrayList<>();

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public List<DeliveryRequest> getLocations() {
        return locations;
    }

    public void setLocations(List<DeliveryRequest> locations) {
        this.locations = locations;
    }
}