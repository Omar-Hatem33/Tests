package com.team21.uber.location.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LocationAnalyticsDTO {

    private long totalLocationEvents;
    private int activeDrivers;
    private double averageSpeed;
    private Map<Integer, Long> eventsByHour = new LinkedHashMap<>();
    private List<Map<String, Object>> geographicCoverage = new ArrayList<>();

    public LocationAnalyticsDTO() {}

    public static Builder builder() { return new Builder(); }

    public long getTotalLocationEvents() { return totalLocationEvents; }
    public void setTotalLocationEvents(long v) { this.totalLocationEvents = v; }

    public int getActiveDrivers() { return activeDrivers; }
    public void setActiveDrivers(int v) { this.activeDrivers = v; }

    public double getAverageSpeed() { return averageSpeed; }
    public void setAverageSpeed(double v) { this.averageSpeed = v; }

    public Map<Integer, Long> getEventsByHour() { return eventsByHour; }
    public void setEventsByHour(Map<Integer, Long> v) {
        this.eventsByHour = v == null ? new LinkedHashMap<>() : v;
    }

    public List<Map<String, Object>> getGeographicCoverage() { return geographicCoverage; }
    public void setGeographicCoverage(List<Map<String, Object>> v) {
        this.geographicCoverage = v == null ? new ArrayList<>() : v;
    }

    public static class Builder {
        private final LocationAnalyticsDTO dto = new LocationAnalyticsDTO();

        public Builder totalLocationEvents(long v) { dto.totalLocationEvents = v; return this; }
        public Builder activeDrivers(int v) { dto.activeDrivers = v; return this; }
        public Builder averageSpeed(double v) { dto.averageSpeed = v; return this; }
        public Builder eventsByHour(Map<Integer, Long> v) {
            dto.eventsByHour = v == null ? new LinkedHashMap<>() : v;
            return this;
        }
        public Builder geographicCoverage(List<Map<String, Object>> v) {
            dto.geographicCoverage = v == null ? new ArrayList<>() : v;
            return this;
        }

        public LocationAnalyticsDTO build() { return dto; }
    }
}
