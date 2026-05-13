package com.team21.uber.ride.controller;

import com.team21.uber.ride.dto.AddStopRequest;
import com.team21.uber.ride.dto.FareEstimateDTO;
import com.team21.uber.ride.dto.FareEstimateRequest;
import com.team21.uber.ride.dto.RideAnalyticsDTO;
import com.team21.uber.ride.dto.RideDetailsDTO;
import com.team21.uber.ride.model.Ride;
import com.team21.uber.ride.service.RideService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;


import java.util.Map;

@RestController
@RequestMapping("api/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PutMapping("/{id}/status")
    public Ride updateRideStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return rideService.updateRideStatus(id, body.get("status"));
    }

    // ── S3-f1 ──────────────────────────────────────────
    @GetMapping("/search")
    public List<Ride> searchRides(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return rideService.searchRides(status, userId, driverId, startDate, endDate);
    }

    // S3-F2
    @PutMapping("/{rideId}/assign")
    public ResponseEntity<Ride> assignDriver(
            @PathVariable Long rideId,
            @RequestParam Long driverId) {
        return ResponseEntity.ok(rideService.assignDriver(rideId, driverId));
    }

    // S3-F3
    @PostMapping("/estimate")
    public ResponseEntity<FareEstimateDTO> getFareEstimate(@RequestBody FareEstimateRequest request) {
        return ResponseEntity.ok(rideService.getFareEstimate(request));
    }

    // S3-F4
    @PutMapping("/{id}/complete")
    public ResponseEntity<Ride> completeRide(@PathVariable Long id) {
        return ResponseEntity.ok(rideService.completeRide(id));
    }

    // S3-F5
    @GetMapping("/metadata/search")
    public ResponseEntity<List<Ride>> filterByMetadata(
            @RequestParam String key,
            @RequestParam String value) {
        return ResponseEntity.ok(rideService.filterByMetadata(key, value));
    }

    // S3-F6
    @GetMapping("/analytics")
    public ResponseEntity<RideAnalyticsDTO> getRideAnalytics(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
        LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
        return ResponseEntity.ok(rideService.getRideAnalytics(start, end));
    }
    // S3-F7
    @PutMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelRide(@PathVariable Long id) {
        rideService.cancelRide(id);
        return ResponseEntity.ok().build();
    }

    // S3-F8
    @PostMapping("/{rideId}/stops")
    public ResponseEntity<Ride> addStops(
            @PathVariable Long rideId,
            @RequestBody Object body) {
        java.util.List<?> raw = null;
        if (body instanceof java.util.List<?> l) {
            raw = l;
        } else if (body instanceof java.util.Map<?, ?> m && m.get("stops") instanceof java.util.List<?> l2) {
            raw = l2;
        }
        if (raw == null || raw.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stops list must be non-empty");
        }
        List<AddStopRequest> stops = new java.util.ArrayList<>();
        java.util.Set<Integer> seenOrders = new java.util.HashSet<>();
        for (Object o : raw) {
            if (!(o instanceof java.util.Map<?, ?> m)) continue;
            Object lat = m.get("latitude");
            Object lon = m.get("longitude");
            Object addr = m.get("address");
            Object meta = m.get("metadata");
            Object order = m.get("stopOrder");
            if (lat == null || lon == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "latitude and longitude required for each stop");
            }
            if (order instanceof Number n) {
                int ord = n.intValue();
                if (!seenOrders.add(ord)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "duplicate stopOrder: " + ord);
                }
            }
            AddStopRequest req = new AddStopRequest();
            if (lat instanceof Number n) req.setLatitude(n.doubleValue());
            if (lon instanceof Number n) req.setLongitude(n.doubleValue());
            if (addr != null) req.setAddress(addr.toString());
            if (meta instanceof java.util.Map<?, ?> mm) {
                java.util.Map<String, Object> typed = new java.util.HashMap<>();
                mm.forEach((k, v) -> typed.put(String.valueOf(k), v));
                req.setMetadata(typed);
            }
            stops.add(req);
        }
        if (stops.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stops list must be non-empty");
        }
        return ResponseEntity.ok(rideService.addStops(rideId, stops));
    }

    // S3-F9
    @GetMapping("/{rideId}/details")
    public ResponseEntity<RideDetailsDTO> getRideDetails(@PathVariable Long rideId) {
        return ResponseEntity.ok(rideService.getRideDetails(rideId));
    }

    // S3-F12: recommendations
    @GetMapping("/recommendations")
    public ResponseEntity<List<Map<String, Object>>> getRecommendations(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer limit) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId required");
        }
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && auth != null && auth.getPrincipal() instanceof Long callerId
                && !callerId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot view other user's recommendations");
        }
        int lim = limit != null ? limit : 5;
        // Check user exists in postgres users table — 404 if not
        String userName = rideService.findUserName(userId);
        if (userName == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        }
        List<Map<String, Object>> recs = rideService.getRecommendations(userId, lim);
        return ResponseEntity.ok(recs);
    }

    // S3-F11: Record User-Driver Riding Pattern
    @PostMapping("/{rideId}/record-interaction")
    public ResponseEntity<Map<String, Object>> recordInteraction(@PathVariable Long rideId) {
        Map<String, Object> result = rideService.recordInteraction(rideId);
        return ResponseEntity.ok(result);
    }

    // ── Ride CRUD ──────────────────────────────────────────

    // Ride CRUD
    @PostMapping
    public ResponseEntity<Ride> createRide(@RequestBody Ride ride) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rideService.createRide(ride));
    }

    @GetMapping
    public List<Ride> getAllRides() {
        return rideService.getAllRides();
    }

    @GetMapping("/{id}")
    public Ride getRideById(@PathVariable Long id) {
        return rideService.getRideById(id);
    }

    @PutMapping("/{id}")
    public Ride updateRide(@PathVariable Long id, @RequestBody Ride ride) {
        return rideService.updateRide(id, ride);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRide(@PathVariable Long id) {
        rideService.deleteRide(id);
        return ResponseEntity.noContent().build();
    }
}

