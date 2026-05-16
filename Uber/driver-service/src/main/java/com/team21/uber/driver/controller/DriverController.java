package com.team21.uber.driver.controller;

import com.team21.uber.driver.dto.*;
import com.team21.uber.driver.model.Driver;
import com.team21.uber.driver.model.DriverStatus;
import com.team21.uber.driver.service.DriverService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    // ── Availability endpoint (called by ride-service via Feign) ──

    @GetMapping("/{id}/availability")
    public ResponseEntity<DriverAvailabilityDTO> getDriverAvailability(@PathVariable Long id) {
        Driver driver = driverService.getDriverByIdOrThrow(id);
        return ResponseEntity.ok(new DriverAvailabilityDTO(driver.getId(), driver.getStatus()));
    }

    // ── CRUD ─────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Driver> createDriver(@RequestBody Driver driver) {
        return ResponseEntity.ok(driverService.createDriver(driver));
    }

    @GetMapping
    public ResponseEntity<List<Driver>> getAllDrivers() {
        return ResponseEntity.ok(driverService.getAllDrivers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Driver> getDriverById(@PathVariable Long id) {
        return ResponseEntity.ok(driverService.getDriverByIdOrThrow(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Driver> updateDriver(@PathVariable Long id, @RequestBody Driver driver) {
        return ResponseEntity.ok(driverService.updateDriver(id, driver));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDriver(@PathVariable Long id) {
        driverService.deleteDriver(id);
        return ResponseEntity.noContent().build();
    }

    // ── S2-F1: Search Drivers (PostgreSQL) ───────────────────────

    @GetMapping("/search")
    public ResponseEntity<List<Driver>> searchDrivers(
            @RequestParam(required = false) DriverStatus status,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating) {
        return ResponseEntity.ok(driverService.searchDrivers(status, minRating, maxRating));
    }

   

    // ── S2-F10: Full-Text Driver Search (Elasticsearch) ──────────

    @GetMapping("/search/full-text")
    public ResponseEntity<List<DriverSearchResultDTO>> fullTextSearch(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Double maxRating) {
        if (minRating != null && (minRating < 0 || minRating > 5)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "minRating must be in [0,5]");
        }
        if (maxRating != null && (maxRating < 0 || maxRating > 5)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "maxRating must be in [0,5]");
        }
        if (minRating != null && maxRating != null && minRating > maxRating) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "minRating must be <= maxRating");
        }
        return ResponseEntity.ok(
                driverService.fullTextSearch(query, vehicleType, status, minRating, maxRating));
    }

    // ── S2-F11: Index Driver into Elasticsearch ───────────────
    @PostMapping("/{id}/index")
    public ResponseEntity<Map<String, Object>> indexDriver(
            @PathVariable Long id) {
        return ResponseEntity.ok(driverService.indexDriver(id, "explicit"));
    }
    // ── S2-F12: Driver Performance Dashboard ─────────────────────
    @GetMapping("/{id}/dashboard")
    public ResponseEntity<DriverDashboardDTO> getDriverDashboard(@PathVariable Long id) {
            // ALWAYS log (even cache hits)
            driverService.publish("DASHBOARD_VIEWED", id, Map.of());
        return ResponseEntity.ok(driverService.getDriverDashboard(id));
    }


    // ── S2-F2: Update Vehicle Details ────────────────────────────

    @PutMapping("/{id}/vehicle")
    public ResponseEntity<?> updateVehicleDetails(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> vehicleDetails) {
        if (vehicleDetails == null || vehicleDetails.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Request body must not be empty");
        }
        Optional<Driver> optionalDriver = driverService.getDriverById(id);
        if (optionalDriver.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Driver not found");
        }
        return ResponseEntity.ok(driverService.updateVehicleDetails(optionalDriver.get(), vehicleDetails));
    }

    // ── S2-F3: Driver Earnings ───────────────────────────────────

    @GetMapping("/{id}/earnings")
    public DriverEarningsDTO getDriverEarnings(
            @PathVariable Long id,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        return driverService.getDriverEarnings(
                id,
                LocalDate.parse(startDate).atStartOfDay(),
                LocalDate.parse(endDate).atTime(23, 59, 59));
    }

    // ── S2-F4: Update Driver Availability ───────────────────────

    @PutMapping("/{id}/availability")
    public ResponseEntity<?> updateAvailability(@PathVariable Long id,
                                                @RequestBody AvailabilityUpdateRequest request) {
        Optional<Driver> optionalDriver = driverService.getDriverById(id);
        if (optionalDriver.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Driver not found");
        }
        if (request == null || request.getStatus() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Status is required");
        }
        if (request.getStatus() == DriverStatus.OFFLINE && driverService.hasActiveRides(id)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Driver cannot go OFFLINE while having active rides");
        }
        driverService.updateAvailability(optionalDriver.get(), request);
        return ResponseEntity.ok().build();
    }

    // ── S2-F5: Filter by Vehicle Type ────────────────────────────

    @GetMapping("/vehicle-type")
    public ResponseEntity<List<Driver>> getDriversByVehicleType(
            @RequestParam String type,
            @RequestParam(required = false) DriverStatus status) {
        return ResponseEntity.ok(driverService.getDriversByVehicleType(type, status));
    }

    // ── S2-F6: Top Rated Drivers ─────────────────────────────────

    @GetMapping("/reports/top-rated")
    public List<TopDriverDTO> getTopRatedDrivers(@RequestParam int limit) {
        return driverService.getTopRatedDrivers(limit);
    }

    // ── S2-F7: Rate a Driver ─────────────────────────────────────

    @PostMapping("/{id}/rate")
    public ResponseEntity<?> rateDriver(@PathVariable Long id,
                                        @RequestBody DriverRatingRequest request) {
        Optional<Driver> optionalDriver = driverService.getDriverById(id);
        if (optionalDriver.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Driver not found");
        }
        if (request == null || request.getRideId() == null || request.getRating() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("rideId and rating are required");
        }
        if (request.getRating() < 1 || request.getRating() > 5) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Rating must be between 1 and 5");
        }
        // M3: ride validation now handled inside rateDriver() via Feign → ride-service
        driverService.rateDriver(optionalDriver.get(), request);
        return ResponseEntity.ok().build();
    }

    // ── S2-F8: Verify Driver Document ────────────────────────────

    @PutMapping("/{driverId}/documents/{documentId}/verify")
    public Driver verifyDocument(
            @PathVariable Long driverId,
            @PathVariable Long documentId,
            @RequestBody(required = false) Map<String, Object> body) {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        Long verifiedBy = null;
        if (body != null && body.get("verifiedBy") != null) {
            try { verifiedBy = Long.valueOf(body.get("verifiedBy").toString()); }
            catch (NumberFormatException ignored) {}
        }
        if (verifiedBy == null && auth != null && auth.getPrincipal() instanceof Long uid) {
            verifiedBy = uid;
        }
        if (verifiedBy == null) verifiedBy = 0L;
        return driverService.verifyDriverDocument(driverId, documentId, verifiedBy);
    }

    // ── S2-F9: Drivers with Expired Documents ────────────────────

    @GetMapping("/documents/expired")
    public ResponseEntity<List <DriverDocumentAlertDTO>> getExpiredDocuments() {
        return ResponseEntity.ok(driverService.getDriversWithExpiredDocuments());
    }

    // ── Misc ──────────────────────────────────────────────────────

    @GetMapping("/{driverId}/rating")
    public ResponseEntity<Double> getDriverRating(@PathVariable Long driverId) {
        return ResponseEntity.ok(driverService.getDriverRating(driverId));
    }

    @PatchMapping("/{driverId}/status")
    public ResponseEntity<Driver> updateDriverStatus(
            @PathVariable Long driverId,
            @RequestParam DriverStatus status) {
        return ResponseEntity.ok(driverService.updateDriverStatus(driverId, status));
    }



}