package com.team21.uber.ride.neo4j;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RelationshipProperties
public class RodeWithRelationship {

    @Id
    @GeneratedValue
    private Long id;

    @TargetNode
    private DriverNode driver;

    private int rideCount;

    private LocalDateTime lastRideDate;

    private List<Long> recordedRideIds = new ArrayList<>();

    public RodeWithRelationship() {}

    public RodeWithRelationship(DriverNode driver, int rideCount, LocalDateTime lastRideDate) {
        this.driver = driver;
        this.rideCount = rideCount;
        this.lastRideDate = lastRideDate;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DriverNode getDriver() { return driver; }
    public void setDriver(DriverNode driver) { this.driver = driver; }

    public int getRideCount() { return rideCount; }
    public void setRideCount(int rideCount) { this.rideCount = rideCount; }

    public LocalDateTime getLastRideDate() { return lastRideDate; }
    public void setLastRideDate(LocalDateTime lastRideDate) { this.lastRideDate = lastRideDate; }

    public List<Long> getRecordedRideIds() { return recordedRideIds; }
    public void setRecordedRideIds(List<Long> recordedRideIds) { this.recordedRideIds = recordedRideIds; }
}
