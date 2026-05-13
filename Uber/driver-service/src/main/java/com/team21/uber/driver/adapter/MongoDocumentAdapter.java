package com.team21.uber.driver.adapter;

import com.team21.uber.driver.dto.RecentActivityDTO;
import com.team21.uber.driver.events.DriverEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts Mongo DriverEvent documents into the slim
 * RecentActivityDTO surfaced by the dashboard endpoint.
 */
@Component
public class MongoDocumentAdapter {


    public RecentActivityDTO adapt(DriverEvent event) {
        if (event == null) return null;
        Map<String, Object> details = event.getDetails() != null
                ? new HashMap<>(event.getDetails())
                : new HashMap<>();
        return RecentActivityDTO.builder()
                .id(event.getId())
                .action(event.getAction())
                .timestamp(event.getTimestamp())
                .details(details)
                .build();
    }
}
