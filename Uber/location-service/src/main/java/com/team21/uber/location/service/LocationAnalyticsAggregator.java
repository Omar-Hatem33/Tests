package com.team21.uber.location.service;

import com.team21.uber.location.adapter.CassandraRowAdapter;
import com.team21.uber.location.adapter.MongoDocumentAdapter;
import com.team21.uber.location.dto.LocationAnalyticsDTO;
import com.team21.uber.location.dto.LocationTrackingDTO;
import com.team21.uber.location.events.LocationEvent;
import com.team21.uber.location.events.LocationEventRepository;
import com.team21.uber.location.model.Location;
import com.team21.uber.location.model.LocationTrackingEvent;
import com.team21.uber.location.repository.DeliveryRepository;
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

@Service
public class LocationAnalyticsAggregator {

    private static final Logger log = LoggerFactory.getLogger(LocationAnalyticsAggregator.class);

    private final ObjectProvider<LocationEventRepository> locationEventRepoProvider;
    private final ObjectProvider<LocationTrackingRepository> trackingRepoProvider;
    private final DeliveryRepository deliveryRepository;
    private final MongoDocumentAdapter mongoDocumentAdapter;
    private final CassandraRowAdapter cassandraRowAdapter;

    public LocationAnalyticsAggregator(ObjectProvider<LocationEventRepository> locationEventRepoProvider,
                                       ObjectProvider<LocationTrackingRepository> trackingRepoProvider,
                                       DeliveryRepository deliveryRepository,
                                       MongoDocumentAdapter mongoDocumentAdapter,
                                       CassandraRowAdapter cassandraRowAdapter) {
        this.locationEventRepoProvider = locationEventRepoProvider;
        this.trackingRepoProvider = trackingRepoProvider;
        this.deliveryRepository = deliveryRepository;
        this.mongoDocumentAdapter = mongoDocumentAdapter;
        this.cassandraRowAdapter = cassandraRowAdapter;
    }

    @Cacheable(cacheNames = "report-10m", key = "'analytics:' + #startDate + ':' + #endDate")
    public LocationAnalyticsDTO aggregateAnalytics(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startLdt = startDate.atStartOfDay();
        LocalDateTime endLdt = endDate.atTime(LocalTime.MAX);
        Instant startInstant = startLdt.toInstant(ZoneOffset.UTC);
        Instant endInstant = endLdt.toInstant(ZoneOffset.UTC);

        long totalLocationEvents = 0L;
        Set<String> drivers = new HashSet<>();
        List<Map<String, Object>> geographicCoverage = new ArrayList<>();
        Map<Integer, Long> eventsByHour = new LinkedHashMap<>();
        double speedSum = 0.0;
        long speedSamples = 0L;

        try {
            List<Location> locations = deliveryRepository.findByTimestampBetweenOrderByTimestampAsc(startLdt, endLdt);
            for (Location loc : locations) {
                if (loc == null || loc.getTimestamp() == null) continue;
                totalLocationEvents++;
                if (loc.getDriverId() != null) drivers.add(String.valueOf(loc.getDriverId()));
                if (loc.getLatitude() != null && loc.getLongitude() != null) {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("driverId", loc.getDriverId());
                    entry.put("latitude", loc.getLatitude());
                    entry.put("longitude", loc.getLongitude());
                    geographicCoverage.add(entry);
                }
                Map<String, Object> meta = loc.getMetadata();
                if (meta != null && meta.get("speed") instanceof Number speedNum) {
                    speedSum += speedNum.doubleValue();
                    speedSamples++;
                }
                int hour = loc.getTimestamp().getHour();
                eventsByHour.merge(hour, 1L, Long::sum);
            }
        } catch (Exception ex) {
            log.warn("Postgres locations query failed for analytics: {}", ex.getMessage());
        }

        LocationTrackingRepository trackingRepo = trackingRepoProvider.getIfAvailable();
        if (trackingRepo != null) {
            try {
                Iterable<LocationTrackingEvent> rows = trackingRepo.findAll();
                for (LocationTrackingEvent row : rows) {
                    if (row == null || row.getTimestamp() == null) continue;
                    if (row.getTimestamp().isBefore(startInstant) || row.getTimestamp().isAfter(endInstant)) continue;

                    LocationTrackingDTO dto = cassandraRowAdapter.adapt(row);
                    if (dto == null) continue;

                    totalLocationEvents++;
                    if (dto.getDriverId() != null) drivers.add(dto.getDriverId());

                    if (dto.getLatitude() != null && dto.getLongitude() != null) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("driverId", dto.getDriverId());
                        entry.put("latitude", dto.getLatitude());
                        entry.put("longitude", dto.getLongitude());
                        geographicCoverage.add(entry);
                    }

                    if (dto.getSpeed() != null) {
                        speedSum += dto.getSpeed();
                        speedSamples++;
                    }

                    int hour = row.getTimestamp().atZone(ZoneOffset.UTC).getHour();
                    eventsByHour.merge(hour, 1L, Long::sum);
                }
            } catch (Exception ex) {
                log.warn("Cassandra unavailable for analytics: {}", ex.getMessage());
            }
        }

        LocationEventRepository mongoRepo = locationEventRepoProvider.getIfAvailable();
        if (mongoRepo != null) {
            try {
                List<LocationEvent> all = mongoRepo.findAll();
                for (LocationEvent ev : all) {
                    if (ev == null || ev.getTimestamp() == null) continue;
                    if (ev.getTimestamp().isBefore(startLdt) || ev.getTimestamp().isAfter(endLdt)) continue;
                    Map<String, Object> adapted = mongoDocumentAdapter.adapt(ev);
                    Object did = adapted.get("driverId");
                    if (did != null) drivers.add(String.valueOf(did));
                }
            } catch (Exception ex) {
                log.warn("Mongo unavailable for analytics: {}", ex.getMessage());
            }
        }

        double averageSpeed = speedSamples > 0 ? speedSum / speedSamples : 0.0;

        return LocationAnalyticsDTO.builder()
                .totalLocationEvents(totalLocationEvents)
                .activeDrivers(drivers.size())
                .averageSpeed(averageSpeed)
                .eventsByHour(eventsByHour)
                .geographicCoverage(geographicCoverage)
                .build();
    }

    public List<LocationTrackingDTO> aggregateTimeline(String driverId, Instant startTime, Instant endTime, Integer limit) {
        List<LocationTrackingDTO> result = new ArrayList<>();

        Long driverIdLong = null;
        try { driverIdLong = Long.valueOf(driverId); } catch (NumberFormatException ignored) {}

        if (driverIdLong != null) {
            try {
                LocalDateTime startLdt = startTime == null ? LocalDateTime.of(1970, 1, 1, 0, 0)
                        : LocalDateTime.ofInstant(startTime, ZoneOffset.UTC);
                LocalDateTime endLdt = endTime == null ? LocalDateTime.now().plusYears(10)
                        : LocalDateTime.ofInstant(endTime, ZoneOffset.UTC);
                List<Location> locations = deliveryRepository
                        .findByDriverIdAndTimestampBetweenOrderByTimestampAsc(driverIdLong, startLdt, endLdt);
                for (Location loc : locations) {
                    if (loc == null) continue;
                    Double speed = null;
                    Map<String, Object> meta = loc.getMetadata();
                    if (meta != null && meta.get("speed") instanceof Number n) speed = n.doubleValue();
                    LocationTrackingDTO dto = LocationTrackingDTO.builder()
                            .trackingId(null)
                            .driverId(driverId)
                            .latitude(loc.getLatitude())
                            .longitude(loc.getLongitude())
                            .speed(speed)
                            .timestamp(loc.getTimestamp() == null ? null : loc.getTimestamp().toInstant(ZoneOffset.UTC))
                            .success(true)
                            .build();
                    result.add(dto);
                }
            } catch (Exception ex) {
                log.warn("Postgres timeline query failed: {}", ex.getMessage());
            }
        }

        LocationTrackingRepository repo = trackingRepoProvider.getIfAvailable();
        if (repo != null && driverIdLong != null) {
            try {
                List<LocationTrackingEvent> rows;
                if (startTime != null && endTime != null) {
                    rows = repo.findByDriverIdAndTimestampRange(driverIdLong, startTime, endTime);
                } else {
                    rows = repo.findByDriverId(driverIdLong);
                }
                for (LocationTrackingEvent row : rows) {
                    LocationTrackingDTO dto = cassandraRowAdapter.adapt(row);
                    if (dto != null) result.add(dto);
                }
            } catch (Exception ex) {
                log.warn("Cassandra unavailable for timeline: {}", ex.getMessage());
            }
        }

        result.sort(Comparator.comparing(LocationTrackingDTO::getTimestamp,
                Comparator.nullsLast(Comparator.reverseOrder())));
        int max = (limit == null || limit <= 0) ? result.size() : Math.min(limit, result.size());
        return new ArrayList<>(result.subList(0, max));
    }
}
