package com.team21.uber.location.controller;

import com.team21.uber.location.dto.*;
import com.team21.uber.location.service.DeliveryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public ResponseEntity<List<DeliveryResponse>> getAllDeliveries() {
        return ResponseEntity.ok(deliveryService.getAllDeliveries());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeliveryResponse> getDelivery(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryService.getDelivery(id));
    }

    @PostMapping("/driver/{driverId}")
    public ResponseEntity<DeliveryResponse> createLocation(@PathVariable Long driverId, @RequestBody DeliveryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.createDeliveryForDriver(driverId, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeliveryResponse> updateDelivery(@PathVariable Long id, @RequestBody DeliveryRequest request) {
        return ResponseEntity.ok(deliveryService.updateDelivery(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDelivery(@PathVariable Long id) {
        deliveryService.deleteDelivery(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/driver/{driverId}/latest")
    public ResponseEntity<DeliveryResponse> getLatestDelivery(@PathVariable Long driverId) {
        return ResponseEntity.ok(deliveryService.getLatestDelivery(driverId));
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<NearbyDriverDTO>> getNearbyDeliveries(@RequestParam double lat,
                                                                       @RequestParam double lon,
                                                                       @RequestParam double radiusKm) {
        return ResponseEntity.ok(deliveryService.findNearbyDeliveries(lat, lon, radiusKm));
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchDeliveryResponse> createBatch(@RequestBody BatchDeliveryUpdateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(deliveryService.createBatch(request));
    }
    @GetMapping("/metadata/search")
    public ResponseEntity<List<DeliveryResponse>> searchByMetadata(@RequestParam String key,
                                                                   @RequestParam String operator,
                                                                   @RequestParam String value) {
        return ResponseEntity.ok(deliveryService.searchByMetadata(key, operator, value));
    }
    @GetMapping("/history")
    public ResponseEntity<List<DeliveryResponse>> getHistory(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                             @RequestParam(required = false) Long driverId) {
        return ResponseEntity.ok(deliveryService.getLocationHistory(startDate, endDate, driverId));
    }

    @DeleteMapping("/purge")
    public ResponseEntity<PurgeResponse> purgeOldDeliveries(@RequestParam int olderThanDays) {
        return ResponseEntity.ok(deliveryService.purgeOldDeliveries(olderThanDays));
    }

    @GetMapping("/driver/{driverId}/summary")
    public ResponseEntity<DriverMovementSummaryDTO> getDriverSummary(@PathVariable Long driverId,
                                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(deliveryService.getDriverSummary(driverId, startDate, endDate));
    }

    @GetMapping("/stationary") // f9
    public ResponseEntity<List<StationaryDriverDTO>> getStationaryDrivers(@RequestParam double maxSpeed,
                                                                         @RequestParam long sinceMinutes) {
        return ResponseEntity.ok(deliveryService.findDelayedDeliveries(maxSpeed, sinceMinutes));
    }



}