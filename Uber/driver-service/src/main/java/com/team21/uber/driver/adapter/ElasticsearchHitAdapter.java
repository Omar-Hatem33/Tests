package com.team21.uber.driver.adapter;

import com.team21.uber.driver.dto.DriverSearchResultDTO;
import com.team21.uber.driver.model.Driver;
import com.team21.uber.driver.model.DriverSearchDocument;
import org.springframework.stereotype.Component;

/**
 * Adapter pattern (DP-7): converts Elasticsearch hits (DriverSearchDocument)
 * into the outbound DriverSearchResultDTO.
 * Also exposes a JPA Driver fallback path for when ES is unavailable.
 */
@Component
public class ElasticsearchHitAdapter {

    /**
     * Primary adapt path: ES document → DTO.
     * The document's id field holds the PG driver id as a string.
     */
    public DriverSearchResultDTO adapt(DriverSearchDocument doc) {
        if (doc == null) return null;
        return DriverSearchResultDTO.builder()
                .driverId(doc.getId())          // id IS the PG driver id
                .name(doc.getName())
                .status(doc.getStatus())
                .vehicleType(doc.getVehicleType())
                .rating(doc.getRating())
                .description(doc.getDescription())
                .build();
    }

    /**
     * Fallback adapt path: JPA Driver entity → DTO.
     * Used when Elasticsearch is unavailable (soft dependency).
     */
    public DriverSearchResultDTO adaptFromDriver(Driver driver) {
        if (driver == null) return null;
        String vehicleType = null;
        String description = null;
        if (driver.getVehicleDetails() != null) {
            Object vt = driver.getVehicleDetails().get("vehicleType");
            if (vt != null) vehicleType = String.valueOf(vt);
            Object desc = driver.getVehicleDetails().get("description");
            if (desc != null) description = String.valueOf(desc);
        }
        return DriverSearchResultDTO.builder()
                .driverId(driver.getId() == null ? null : String.valueOf(driver.getId()))
                .name(driver.getName())
                .status(driver.getStatus() == null ? null : driver.getStatus().name())
                .vehicleType(vehicleType)
                .rating(driver.getRating())
                .description(description)
                .build();
    }
}
