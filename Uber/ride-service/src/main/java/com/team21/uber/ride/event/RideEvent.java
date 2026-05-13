package com.team21.uber.ride.event;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "ride_events")
public class RideEvent implements MongoEvent {

    @Id
    private String id;

    private Long rideId;

    private String action;

    private LocalDateTime timestamp;

    private Map<String, Object> details;

    public RideEvent() {}

    public RideEvent(Long rideId, String action, LocalDateTime timestamp, Map<String, Object> details) {
        this.rideId = rideId;
        this.action = action;
        this.timestamp = timestamp;
        this.details = details;
    }

    @Override
    public String getId() { return id; }

    public void setId(String id) { this.id = id; }

    public Long getRideId() { return rideId; }

    public void setRideId(Long rideId) { this.rideId = rideId; }

    @Override
    public String getAction() { return action; }

    public void setAction(String action) { this.action = action; }

    @Override
    public LocalDateTime getTimestamp() { return timestamp; }

    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public Map<String, Object> getDetails() { return details; }

    public void setDetails(Map<String, Object> details) { this.details = details; }
}
