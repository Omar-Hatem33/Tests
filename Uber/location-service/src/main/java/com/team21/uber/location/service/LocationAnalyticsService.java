package com.team21.uber.location.service;

import com.team21.uber.location.adapter.CassandraRowAdapter;
import com.team21.uber.location.adapter.MongoDocumentAdapter;
import com.team21.uber.location.dto.LocationAnalyticsDTO;
import com.team21.uber.location.dto.LocationTrackingDTO;
import com.team21.uber.location.dto.LocationTrackingRequest;
import com.team21.uber.location.events.EventPublisher;
import com.team21.uber.location.events.LocationEvent;
import com.team21.uber.location.events.LocationEventRepository;
import com.team21.uber.location.exception.BadRequestException;
import com.team21.uber.location.model.LocationTrackingEvent;
import com.team21.uber.location.repository.LocationTrackingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Service for the M2 location analytics + tracking endpoints (S4-F10/F11/F12).
 * All NoSQL infrastructure is optional via ObjectProvider — soft fails when unavailable.
 */
@Service
public class LocationAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(LocationAnalyticsService.class);

    private final EventPublisher eventPublisher;
    private final ObjectProvider<LocationEventRepository> locationEventRepoProvider;
    private final ObjectProvider<LocationTrackingRepository> trackingRepoProvider;
    private final MongoDocumentAdapter mongoDocumentAdapter;
    private final CassandraRowAdapter cassandraRowAdapter;
    private final LocationAnalyticsAggregator aggregator;

    public LocationAnalyticsService(EventPublisher eventPublisher,
                                    ObjectProvider<LocationEventRepository> locationEventRepoProvider,
                                    ObjectProvider<LocationTrackingRepository> trackingRepoProvider,
                                    MongoDocumentAdapter mongoDocumentAdapter,
                                    CassandraRowAdapter cassandraRowAdapter,
                                    LocationAnalyticsAggregator aggregator) {
        this.eventPublisher = eventPublisher;
        this.locationEventRepoProvider = locationEventRepoProvider;
        this.trackingRepoProvider = trackingRepoProvider;
        this.mongoDocumentAdapter = mongoDocumentAdapter;
        this.cassandraRowAdapter = cassandraRowAdapter;
        this.aggregator = aggregator;
    }

    /* ----------------------------- helpers ----------------------------- */

    private void publish(String action, String driverId, Map<String, Object> extra) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("driverId", driverId);
            payload.put("action", action);
            if (extra != null) payload.putAll(extra);
            eventPublisher.notifyObservers(action, payload);
        } catch (Exception ex) {
            log.warn("Failed to publish event {}: {}", action, ex.getMessage());
        }
    }

    private void validateDriverId(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            throw new BadRequestException("driverId must not be blank");
        }
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new BadRequestException("latitude and longitude are required");
        }
        if (latitude < -90 || latitude > 90) {
            throw new BadRequestException("Latitude must be between -90 and 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new BadRequestException("Longitude must be between -180 and 180");
        }
    }

    /* --------------------------- S4-F10 dashboard --------------------------- */

    public LocationAnalyticsDTO getAnalytics(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate must be before or equal to endDate");
        }

        LocationAnalyticsDTO dto = aggregator.aggregateAnalytics(startDate, endDate);

        Map<String, Object> details = new HashMap<>();
        details.put("startDate", startDate.toString());
        details.put("endDate", endDate.toString());
        details.put("totalLocationEvents", dto.getTotalLocationEvents());
        details.put("activeDrivers", dto.getActiveDrivers());
        publish("ANALYTICS_VIEWED", null, Map.of("details", details));

        return dto;
    }

    /* --------------------------- S4-F11 record tracking --------------------------- */

    public LocationTrackingDTO recordTracking(String driverId, LocationTrackingRequest request) {
        validateDriverId(driverId);
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        validateCoordinates(request.getLatitude(), request.getLongitude());

        UUID trackingId = UUID.randomUUID();
        Instant now = Instant.now();
        boolean success = false;

        Long driverIdLong = null;
        try { driverIdLong = Long.valueOf(driverId); } catch (NumberFormatException ignored) {}

        LocationTrackingRepository repo = trackingRepoProvider.getIfAvailable();
        if (repo != null && driverIdLong != null) {
            try {
                LocationTrackingEvent row = new LocationTrackingEvent(
                        driverIdLong, now, trackingId,
                        request.getLatitude(), request.getLongitude(), request.getAccuracy(),
                        request.getSpeed(), request.getHeading(), request.getNotes());
                repo.save(row);
                success = true;
            } catch (Exception ex) {
                log.warn("Cassandra save failed for tracking record (driver={}): {}", driverId, ex.toString());
            }
        } else if (repo == null) {
            log.warn("Cassandra repository not available; tracking row not persisted for driver {}", driverId);
        }

        Map<String, Object> details = new HashMap<>();
        details.put("trackingId", trackingId.toString());
        details.put("driverId", driverId);
        details.put("latitude", request.getLatitude());
        details.put("longitude", request.getLongitude());
        details.put("accuracy", request.getAccuracy());
        details.put("speed", request.getSpeed());
        details.put("heading", request.getHeading());
        details.put("notes", request.getNotes());
        details.put("success", success);
        publish("TRACKING_RECORDED", driverId, Map.of("details", details));

        return LocationTrackingDTO.builder()
                .trackingId(trackingId)
                .driverId(driverId)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracy(request.getAccuracy())
                .speed(request.getSpeed())
                .heading(request.getHeading())
                .notes(request.getNotes())
                .timestamp(now)
                .success(success)
                .build();
    }

    /* --------------------------- S4-F12 timeline --------------------------- */

    public List<LocationTrackingDTO> getTimeline(String driverId, Instant startTime, Instant endTime, Integer limit) {
        validateDriverId(driverId);
        List<LocationTrackingDTO> result = aggregator.aggregateTimeline(driverId, startTime, endTime, limit);

        Map<String, Object> details = new HashMap<>();
        details.put("driverId", driverId);
        details.put("count", result.size());
        publish("TIMELINE_VIEWED", driverId, Map.of("details", details));

        return result;
    }
}
