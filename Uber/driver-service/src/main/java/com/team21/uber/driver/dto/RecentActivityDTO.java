package com.team21.uber.driver.dto;

import java.time.LocalDateTime;
import java.util.Map;

public class RecentActivityDTO {

    private String id;
    private String action;
    private LocalDateTime timestamp;
    private Map<String, Object> details;

    public RecentActivityDTO() {}

    private RecentActivityDTO(Builder builder) {
        this.id = builder.id;
        this.action = builder.action;
        this.timestamp = builder.timestamp;
        this.details = builder.details;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String action;
        private LocalDateTime timestamp;
        private Map<String, Object> details;

        public Builder id(String id) { this.id = id; return this; }
        public Builder action(String action) { this.action = action; return this; }
        public Builder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public Builder details(Map<String, Object> details) { this.details = details; return this; }
        public RecentActivityDTO build() { return new RecentActivityDTO(this); }
    }

    public String getId() { return id; }
    public String getAction() { return action; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public Map<String, Object> getDetails() { return details; }
}