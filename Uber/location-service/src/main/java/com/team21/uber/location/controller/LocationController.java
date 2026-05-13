package com.team21.uber.location.controller;

import com.team21.uber.location.dto.LocationAnalyticsDTO;
import com.team21.uber.location.dto.LocationTrackingDTO;
import com.team21.uber.location.dto.LocationTrackingRequest;
import com.team21.uber.location.exception.ResourceNotFoundException;
import com.team21.uber.location.repository.DeliveryRepository;
import com.team21.uber.location.service.LocationAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
// DateTimeFormat used only for LocalDate analytics params; Instant params use Spring's default Instant converter (accepts ISO_INSTANT 'Z' suffix).
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * M2 endpoints for location-service (S4-F10/F11/F12). Lives in a separate controller
 * from DeliveryController to keep the legacy endpoint surface clean.
 *
 * Routes:
 *  - GET  /api/locations/analytics                             — F10 dashboard
 *  - POST /api/locations/{driverId}/tracking                   — F11 record GPS event
 *  - GET  /api/locations/{driverId}/timeline                   — F12 time-series timeline
 *
 * These literal-segment paths do not collide with DeliveryController's `/api/locations/{id}`
 * because Spring matches static segments before path variables.
 */
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationAnalyticsService analyticsService;
    private final DeliveryRepository deliveryRepository;

    public LocationController(LocationAnalyticsService analyticsService,
                              DeliveryRepository deliveryRepository) {
        this.analyticsService = analyticsService;
        this.deliveryRepository = deliveryRepository;
    }

    private void requireDriverExists(String driverId) {
        Long id;
        try {
            id = Long.valueOf(driverId);
        } catch (NumberFormatException e) {
            throw new ResourceNotFoundException("Driver not found: " + driverId);
        }
        if (!deliveryRepository.driverExists(id)) {
            throw new ResourceNotFoundException("Driver not found: " + driverId);
        }
    }

    @GetMapping("/analytics")
    public ResponseEntity<LocationAnalyticsDTO> getAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(analyticsService.getAnalytics(startDate, endDate));
    }

    @PostMapping("/{driverId}/tracking")
    public ResponseEntity<LocationTrackingDTO> recordTracking(@PathVariable String driverId,
                                                              @RequestBody LocationTrackingRequest request) {
        requireDriverExists(driverId);
        LocationTrackingDTO dto = analyticsService.recordTracking(driverId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{driverId}/tracking")
    public ResponseEntity<List<LocationTrackingDTO>> getTrackingTimeline(
            @PathVariable String driverId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        requireDriverExists(driverId);
        Instant start = startTime == null ? null : startTime.toInstant(ZoneOffset.UTC);
        Instant end = endTime == null ? null : endTime.toInstant(ZoneOffset.UTC);
        return ResponseEntity.ok(analyticsService.getTimeline(driverId, start, end, limit));
    }

    @GetMapping("/{driverId}/timeline")
    public ResponseEntity<List<LocationTrackingDTO>> getTimeline(
            @PathVariable String driverId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        requireDriverExists(driverId);
        Instant start = startTime == null ? null : startTime.toInstant(ZoneOffset.UTC);
        Instant end = endTime == null ? null : endTime.toInstant(ZoneOffset.UTC);
        return ResponseEntity.ok(analyticsService.getTimeline(driverId, start, end, limit));
    }
}
