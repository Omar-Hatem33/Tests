package com.team21.uber.location.repository;

import com.team21.uber.location.model.LocationTrackingEvent;
import org.springframework.data.cassandra.core.mapping.MapId;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

import java.time.Instant;
import java.util.List;

public interface LocationTrackingRepository extends CassandraRepository<LocationTrackingEvent, MapId> {

    @Query("SELECT * FROM location_tracking_events WHERE driver_id = ?0 AND timestamp >= ?1 AND timestamp <= ?2")
    List<LocationTrackingEvent> findByDriverIdAndTimestampRange(Long driverId, Instant from, Instant to);

    @Query("SELECT * FROM location_tracking_events WHERE driver_id = ?0")
    List<LocationTrackingEvent> findByDriverId(Long driverId);
}
