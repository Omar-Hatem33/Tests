package com.team21.uber.user.dto;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class UserActivityFeedItemDTO {

    private String eventId;
    private Long userId;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public UserActivityFeedItemDTO() {}

    private UserActivityFeedItemDTO(Builder b) {
        this.eventId = b.eventId;
        this.userId = b.userId;
        this.action = b.action;
        this.timestamp = b.timestamp;
        this.details = b.details;
    }

    public static Builder builder() { return new Builder(); }

    public String getEventId() { return eventId; }
    public Long getUserId() { return userId; }
    public String getAction() { return action; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Map<String, Object> getDetails() { return details; }

    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public void setAction(String action) { this.action = action; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public void setDetails(Map<String, Object> details) { this.details = details; }

    public static final class Builder {
        private String eventId;
        private Long userId;
        private String action;
        private LocalDateTime timestamp;
        private Map<String, Object> details = new HashMap<>();

        public Builder eventId(String v) { this.eventId = v; return this; }
        public Builder userId(Long v) { this.userId = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder timestamp(LocalDateTime v) { this.timestamp = v; return this; }
        public Builder details(Map<String, Object> v) { this.details = v; return this; }

        public UserActivityFeedItemDTO build() { return new UserActivityFeedItemDTO(this); }
    }
}
