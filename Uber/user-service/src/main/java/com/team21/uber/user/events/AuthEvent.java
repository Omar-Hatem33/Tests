package com.team21.uber.user.events;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "auth_events")
public class AuthEvent implements MongoEvent {

    @Id
    private String id;

    // Spec §7.1.2: userId is Long — references User.id in PostgreSQL
    private Long userId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details = new HashMap<>();

    public AuthEvent() {}

    public AuthEvent(Long userId, String action, Map<String, Object> details) {
        this.userId = userId;
        this.action = action;
        this.details = details == null ? new HashMap<>() : details;
        this.timestamp = LocalDateTime.now();
    }

    @Override public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    @Override public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    @Override public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override public Object getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }
}