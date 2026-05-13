package com.team21.uber.location.adapter;

import com.team21.uber.location.dto.LocationTrackingDTO;
import com.team21.uber.location.model.LocationTrackingEvent;
import org.springframework.stereotype.Component;

@Component
public class CassandraRowAdapter {

    public LocationTrackingDTO adapt(LocationTrackingEvent row) {
        if (row == null) return null;
        return LocationTrackingDTO.builder()
                .trackingId(row.getTrackingId())
                .driverId(row.getDriverId() == null ? null : String.valueOf(row.getDriverId()))
                .latitude(row.getLatitude())
                .longitude(row.getLongitude())
                .accuracy(row.getAccuracy())
                .speed(row.getSpeed())
                .heading(row.getHeading())
                .notes(row.getNotes())
                .timestamp(row.getTimestamp())
                .build();
    }
}
