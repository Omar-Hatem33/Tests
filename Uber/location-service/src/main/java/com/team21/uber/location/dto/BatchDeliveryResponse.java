package com.team21.uber.location.dto;

import java.util.ArrayList;
import java.util.List;

public class BatchDeliveryResponse {

    private int count;
    private List<DeliveryResponse> locations = new ArrayList<>();

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<DeliveryResponse> getLocations() {
        return locations;
    }

    public void setLocations(List<DeliveryResponse> locations) {
        this.locations = locations;
    }
}