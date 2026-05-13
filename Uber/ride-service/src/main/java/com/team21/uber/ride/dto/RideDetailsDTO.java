package com.team21.uber.ride.dto;

import com.team21.uber.ride.model.RideStop;
import com.team21.uber.ride.model.RideStatus;

import java.util.List;
import java.util.Map;

public class RideDetailsDTO {

    private Long rideId;
    private Long userId;
    private Long driverId;
    private RideStatus status;
    private Double fare;
    private Map<String, Object> metadata;
    private List<RideStop> stops;
    private int totalStops;
    private long completedStops;

    public RideDetailsDTO() {}

    public RideDetailsDTO(Long rideId, Long userId, Long driverId, RideStatus status,
                          Double fare, Map<String, Object> metadata,
                          List<RideStop> stops, int totalStops, long completedStops) {
        this.rideId = rideId;
        this.userId = userId;
        this.driverId = driverId;
        this.status = status;
        this.fare = fare;
        this.metadata = metadata;
        this.stops = stops;
        this.totalStops = totalStops;
        this.completedStops = completedStops;
    }

    public Long getRideId() { return rideId; }
    public void setRideId(Long rideId) { this.rideId = rideId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public RideStatus getStatus() { return status; }
    public void setStatus(RideStatus status) { this.status = status; }

    public Double getFare() { return fare; }
    public void setFare(Double fare) { this.fare = fare; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public List<RideStop> getStops() { return stops; }
    public void setStops(List<RideStop> stops) { this.stops = stops; }

    public int getTotalStops() { return totalStops; }
    public void setTotalStops(int totalStops) { this.totalStops = totalStops; }

    public long getCompletedStops() { return completedStops; }
    public void setCompletedStops(long completedStops) { this.completedStops = completedStops; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Long rideId;
        private Long userId;
        private Long driverId;
        private RideStatus status;
        private Double fare;
        private Map<String, Object> metadata;
        private List<RideStop> stops;
        private int totalStops;
        private long completedStops;

        public Builder rideId(Long v) { this.rideId = v; return this; }
        public Builder userId(Long v) { this.userId = v; return this; }
        public Builder driverId(Long v) { this.driverId = v; return this; }
        public Builder status(RideStatus v) { this.status = v; return this; }
        public Builder fare(Double v) { this.fare = v; return this; }
        public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }
        public Builder stops(List<RideStop> v) { this.stops = v; return this; }
        public Builder totalStops(int v) { this.totalStops = v; return this; }
        public Builder completedStops(long v) { this.completedStops = v; return this; }

        public RideDetailsDTO build() {
            return new RideDetailsDTO(rideId, userId, driverId, status, fare, metadata, stops, totalStops, completedStops);
        }
    }
}
