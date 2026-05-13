package com.team21.uber.location.events;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "location_events")
public class LocationEvent implements MongoEvent {

    @Id
    private String id;
    private Object driverId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details = new HashMap<>();

    public LocationEvent() {}

    public LocationEvent(Object driverId, String action, Map<String, Object> details) {
        this.driverId = driverId;
        this.action = action;
        this.details = details == null ? new HashMap<>() : details;
        this.timestamp = LocalDateTime.now();
    }

    @Override public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Object getDriverId() { return driverId; }
    public void setDriverId(Object driverId) { this.driverId = driverId; }

    @Override public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    @Override public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override public Object getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}
