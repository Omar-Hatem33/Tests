package com.team21.uber.location.model;

import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("location_tracking_events")
public class LocationTrackingEvent {

    @PrimaryKeyColumn(name = "driver_id", type = PrimaryKeyType.PARTITIONED)
    private Long driverId;

    @PrimaryKeyColumn(name = "timestamp", ordinal = 1, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private Instant timestamp;

    @PrimaryKeyColumn(name = "tracking_id", ordinal = 2, type = PrimaryKeyType.CLUSTERED)
    private UUID trackingId;

    private Double latitude;
    private Double longitude;
    private Double accuracy;
    private Double speed;
    private Double heading;
    private String notes;

    public LocationTrackingEvent() {}

    public LocationTrackingEvent(Long driverId, Instant timestamp, UUID trackingId,
                                 Double latitude, Double longitude, Double accuracy,
                                 Double speed, Double heading, String notes) {
        this.driverId = driverId;
        this.timestamp = timestamp;
        this.trackingId = trackingId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.speed = speed;
        this.heading = heading;
        this.notes = notes;
    }

    public Long getDriverId() { return driverId; }
    public void setDriverId(Long driverId) { this.driverId = driverId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public UUID getTrackingId() { return trackingId; }
    public void setTrackingId(UUID trackingId) { this.trackingId = trackingId; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }

    public Double getSpeed() { return speed; }
    public void setSpeed(Double speed) { this.speed = speed; }

    public Double getHeading() { return heading; }
    public void setHeading(Double heading) { this.heading = heading; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
