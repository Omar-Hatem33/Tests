package com.team21.uber.ride.controller;

import com.team21.uber.contracts.dto.DriverRideSummaryDTO;
import com.team21.uber.contracts.dto.RideSummaryDTO;
import com.team21.uber.ride.service.RideQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RideQueryController — read-only endpoints exposed for Feign consumption by
 * other services (user-service, driver-service).
 *
 * These are separated from the user-facing {@link RideController} because:
 *   • they are pure read aggregations, no business logic mutation
 *   • their consumers are services, not end users
 *   • their lifecycle and SLA are different from the ride lifecycle endpoints
 *
 * Authorization: the api-gateway propagates X-User-Id / X-User-Role on every
 * inbound request, including the JWT it forwards on Feign calls. Ownership
 * checks (§2.10) are enforced at the CALLING service (e.g. user-service for
 * S1-F3) — not here — because only the caller knows the ownership semantics
 * of its own endpoint. ride-service simply validates the JWT and serves the
 * aggregate.
 */
@RestController
@RequestMapping("/api/rides")
public class RideQueryController {

    private static final Logger log = LoggerFactory.getLogger(RideQueryController.class);

    private final RideQueryService rideQueryService;

    public RideQueryController(RideQueryService rideQueryService) {
        this.rideQueryService = rideQueryService;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // USER-FACING (consumed by user-service)
    // ──────────────────────────────────────────────────────────────────────────

    /** Called by S1-F3 (GET /api/users/{id}/ride-summary). */
    @GetMapping("/user/{userId}/summary")
    public ResponseEntity<RideSummaryDTO> getUserRideSummary(@PathVariable Long userId) {
        log.info("GET /api/rides/user/{}/summary", userId);
        return ResponseEntity.ok(rideQueryService.getUserRideSummary(userId));
    }

    /** Called by S1-F4 (PUT /api/users/{id}/deactivate pre-check). */
    @GetMapping("/user/{userId}/active-count")
    public ResponseEntity<Integer> getUserActiveRideCount(@PathVariable Long userId) {
        log.info("GET /api/rides/user/{}/active-count", userId);
        return ResponseEntity.ok(rideQueryService.getUserActiveRideCount(userId));
    }

    /** Called by S1-F9. */
    @GetMapping("/user/{userId}/completed-count")
    public ResponseEntity<Long> getUserCompletedRideCount(@PathVariable Long userId) {
        log.info("GET /api/rides/user/{}/completed-count", userId);
        return ResponseEntity.ok(rideQueryService.getUserCompletedRideCount(userId));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DRIVER-FACING (consumed by driver-service)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Called by S2-F3 (GET /api/drivers/{id}/earnings) and S2-F12 (driver dashboard).
     * Both startDate and endDate are optional. Format: yyyy-MM-dd or ISO datetime.
     */
    @GetMapping("/driver/{driverId}/summary")
    public ResponseEntity<DriverRideSummaryDTO> getDriverRideSummary(
            @PathVariable Long driverId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        log.info("GET /api/rides/driver/{}/summary start={} end={}", driverId, startDate, endDate);
        return ResponseEntity.ok(rideQueryService.getDriverRideSummary(driverId, startDate, endDate));
    }

    /** Called by S2-F4 (PUT /api/drivers/{id}/availability OFFLINE pre-check). */
    @GetMapping("/driver/{driverId}/active-count")
    public ResponseEntity<Integer> getDriverActiveRideCount(@PathVariable Long driverId) {
        log.info("GET /api/rides/driver/{}/active-count", driverId);
        return ResponseEntity.ok(rideQueryService.getDriverActiveRideCount(driverId));
    }

    /** Called by S2-F6 (top-rated-drivers report). */
    @GetMapping("/driver/{driverId}/completed-count")
    public ResponseEntity<Long> getDriverCompletedRideCount(@PathVariable Long driverId) {
        log.info("GET /api/rides/driver/{}/completed-count", driverId);
        return ResponseEntity.ok(rideQueryService.getDriverCompletedRideCount(driverId));
    }
}
