package com.team21.uber.driver.service;

import com.team21.uber.contracts.dto.DriverRideSummaryDTO;
import com.team21.uber.contracts.feign.RideServiceClient;
import com.team21.uber.contracts.feign.UserServiceClient;
import com.team21.uber.driver.adapter.ElasticsearchHitAdapter;
import com.team21.uber.driver.dto.*;

import com.team21.uber.contracts.dto.RideDTO;
import com.team21.uber.contracts.dto.DriverRideSummaryDTO;
import com.team21.uber.driver.messaging.DriverEventPublisher;
import com.team21.uber.driver.model.*;
import com.team21.uber.driver.repository.DriverDocumentRepository;
import com.team21.uber.driver.repository.DriverRepository;
import com.team21.uber.driver.repository.DriverSearchRepository;
import com.team21.uber.driver.repository.RideNativeRepository;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import feign.FeignException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.team21.uber.driver.events.EventPublisher;
import com.team21.uber.contracts.dto.UserDTO;
import com.team21.uber.contracts.dto.RideDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    private final DriverRepository driverRepository;
    private final RideNativeRepository rideNativeRepository;
    private final DriverDocumentRepository driverDocumentRepository;
    private final ObjectProvider<DriverSearchRepository> searchRepoProvider;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchHitAdapter elasticsearchHitAdapter;
    private final EventPublisher eventPublisher;
    private final RideServiceClient rideServiceClient;
    private final DriverEventPublisher driverEventPublisher;
    private final UserServiceClient userServiceClient;
    private final com.team21.uber.contracts.lock.RedisLockService redisLockService;

    // Self-injected proxy reference so internal calls go through Spring AOP (cache, tx).
    private DriverService self;

    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy DriverService self) {
        this.self = self;
    }

    public DriverService(DriverRepository driverRepository,
                         RideNativeRepository rideNativeRepository,
                         DriverDocumentRepository driverDocumentRepository,
                         ObjectProvider<DriverSearchRepository> searchRepoProvider,
                        ElasticsearchOperations elasticsearchOperations,
                         ElasticsearchHitAdapter elasticsearchHitAdapter,
                         EventPublisher eventPublisher,
                         RideServiceClient rideServiceClient,
                         DriverEventPublisher driverEventPublisher,
                         UserServiceClient userServiceClient,
                         com.team21.uber.contracts.lock.RedisLockService redisLockService) {
        this.driverRepository = driverRepository;
        this.rideNativeRepository = rideNativeRepository;
        this.driverDocumentRepository = driverDocumentRepository;
        this.searchRepoProvider = searchRepoProvider;
        this.elasticsearchOperations = elasticsearchOperations;
        this.elasticsearchHitAdapter = elasticsearchHitAdapter;
        this.eventPublisher = eventPublisher;
        this.rideServiceClient = rideServiceClient;
        this.driverEventPublisher = driverEventPublisher;
        this.userServiceClient=userServiceClient;
        this.redisLockService = redisLockService;
    }

    public void publish(String action, Long driverId, Map<String, Object> extra) {
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

    // ── Driver CRUD ──────────────────────────────────────────────

    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    public Driver getDriverByIdOrThrow(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));
    }

    //-----update driver------
    public Driver updateDriver(Long id, Driver updatedDriver) {
        Driver existing = driverRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));

        if (updatedDriver.getName() != null)           existing.setName(updatedDriver.getName());
        if (updatedDriver.getEmail() != null)          existing.setEmail(updatedDriver.getEmail());
        if (updatedDriver.getPhone() != null)          existing.setPhone(updatedDriver.getPhone());
        if (updatedDriver.getLicenseNumber() != null)  existing.setLicenseNumber(updatedDriver.getLicenseNumber());
        if (updatedDriver.getStatus() != null)         existing.setStatus(updatedDriver.getStatus());
        if (updatedDriver.getRating() != null)         existing.setRating(updatedDriver.getRating());
        if (updatedDriver.getTotalRatings() != null)   existing.setTotalRatings(updatedDriver.getTotalRatings());
        if (updatedDriver.getVehicleDetails() != null) existing.setVehicleDetails(updatedDriver.getVehicleDetails());

        Driver saved = driverRepository.save(existing);
        publish("PROFILE_UPDATED", saved.getId(), Map.of());
        try {
            indexDriver(saved.getId(), "auto_crud_update");
        } catch (Exception ex) {
            log.warn("Auto-index on update failed: {}", ex.getMessage());
        }
        return saved;
    }
    //--delete driver----
    public void deleteDriver(Long id) {
        if (!driverRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found");
        }
        DriverSearchRepository searchRepo = searchRepoProvider.getIfAvailable();
        if (searchRepo != null) {
            try {
                searchRepo.deleteById(String.valueOf(id));
            } catch (Exception ex) {
                log.warn("Auto-remove from ES failed: {}", ex.getMessage());
            }
        }
        driverRepository.deleteById(id);
        publish("DRIVER_DELETED", id, Map.of());
    }

    // ── DriverDocument CRUD ──────────────────────────────────────

    public DriverDocument createDocument(Long driverId, DriverDocument document) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));
        document.setDriver(driver);
        return driverDocumentRepository.save(document);
    }

    public List<DriverDocument> getAllDocuments() {
        return driverDocumentRepository.findAll();
    }

    public DriverDocument getDocumentByIdOrThrow(Long id) {
        return driverDocumentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
    }

    public DriverDocument updateDocument(Long id, DriverDocument updatedDocument) {
        DriverDocument existing = driverDocumentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        if (updatedDocument.getType() != null)        existing.setType(updatedDocument.getType());
        if (updatedDocument.getDocumentUrl() != null) existing.setDocumentUrl(updatedDocument.getDocumentUrl());
        if (updatedDocument.getExpiryDate() != null)  existing.setExpiryDate(updatedDocument.getExpiryDate());
        if (updatedDocument.getVerified() != null)    existing.setVerified(updatedDocument.getVerified());
        if (updatedDocument.getMetadata() != null)    existing.setMetadata(updatedDocument.getMetadata());

        return driverDocumentRepository.save(existing);
    }

    public void deleteDocument(Long id) {
        if (!driverDocumentRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        driverDocumentRepository.deleteById(id);
    }

    // ── CREATE DRIVER ────────────────────────────────────────────

    public Driver createDriver(Driver driver) {
        if (driver.getStatus() == null)       driver.setStatus(DriverStatus.OFFLINE);
        if (driver.getRating() == null)       driver.setRating(0.0);
        if (driver.getTotalRatings() == null) driver.setTotalRatings(0);
        Driver saved = driverRepository.save(driver);
        publish("DRIVER_CREATED", saved.getId(), Map.of());
        try {
            indexDriver(saved.getId(), "auto_crud_create");
        } catch (Exception ex) {
            log.warn("Auto-index on create failed: {}", ex.getMessage());
        }
        return saved;
    }


    // ── S2-F1: Search Drivers ────────────────────────────────────

    public List<Driver> searchDrivers(DriverStatus status, Double minRating, Double maxRating) {
        if (minRating != null && maxRating != null && minRating > maxRating) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "minRating cannot be greater than maxRating");
        }
        if (minRating == null) minRating = 0.0;
        if (maxRating == null) maxRating = 5.0;

        if (status != null) {
            return driverRepository.findByStatusAndRatingBetweenOrderByRatingDesc(status, minRating, maxRating);
        } else {
            return driverRepository.findByRatingBetweenOrderByRatingDesc(minRating, maxRating);
        }
    }

    // ── S2-F10: Full-Text Driver Search (Elasticsearch) ──────────

    public List<DriverSearchResultDTO> fullTextSearch(
            String query,
            String vehicleType,
            String status,
            Double minRating,
            Double maxRating) {

        try {
            // bool query: must = full-text on name+description, filter = exact keyword + range
            var boolQuery = QueryBuilders.bool(b -> {
                if (query != null && !query.isBlank()) {
                    b.must(m -> m.multiMatch(mm -> mm
                            .query(query)
                            .fields("name", "description")
                    ));
                } else {
                    b.must(m -> m.matchAll(ma -> ma));
                }

                // Optional keyword filters (no scoring impact)
                if (vehicleType != null && !vehicleType.isBlank()) {
                    b.filter(f -> f.term(t -> t.field("vehicleType").value(vehicleType)));
                }
                if (status != null && !status.isBlank()) {
                    b.filter(f -> f.term(t -> t.field("status").value(status)));
                }

                // Optional rating range filter
                if (minRating != null || maxRating != null) {
                    final Double min = minRating;
                    final Double max = maxRating;
                    b.filter(f -> f.range(r ->
                            (co.elastic.clients.util.ObjectBuilder<RangeQuery>) RangeQuery.of(rq -> {
                                var nb = rq.number(n -> {
                                    n.field("rating");
                                    if (min != null) n.gte(min);
                                    if (max != null) n.lte(max);
                                    return n;
                                });
                                return nb;
                            })
                    ));
                }

                return b;
            });

            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(q -> q.bool(boolQuery.bool()))
                    .build();

            SearchHits<DriverSearchDocument> hits =
                    elasticsearchOperations.search(nativeQuery, DriverSearchDocument.class);

            return hits.stream()
                    .map(hit -> elasticsearchHitAdapter.adapt(hit.getContent()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            // Soft dependency — ES unavailable, fall back to PostgreSQL
            log.warn("Elasticsearch unavailable, falling back to PostgreSQL: {}", e.getMessage());
            return fallbackFullTextSearch(query, vehicleType, status, minRating, maxRating);
        }
    }

    

    // ── S2-F11: Index Driver into Elasticsearch ──────────────

    public Map<String, Object> indexDriver(Long driverId, String source) {
        Driver driver = getDriverByIdOrThrow(driverId);

        String vehicleType = null;
        String description = "";
        if (driver.getVehicleDetails() != null) {
            Object vt = driver.getVehicleDetails().get("vehicleType");
            if (vt != null) vehicleType = String.valueOf(vt);
            Object desc = driver.getVehicleDetails().get("description");
            if (desc != null) description = String.valueOf(desc);
        }

        DriverSearchDocument doc = new DriverSearchDocument(
                String.valueOf(driver.getId()),
                driver.getName(),
                description,
                driver.getStatus() == null ? null : driver.getStatus().name(),
                vehicleType,
                driver.getRating()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("driverId", String.valueOf(driver.getId()));

        DriverSearchRepository searchRepo = searchRepoProvider.getIfAvailable();
        boolean indexed = false;
        String message;
        if (searchRepo == null) {
            message = "Elasticsearch not available; index skipped";
            log.warn(message);
        } else {
            try {
                searchRepo.save(doc);
                indexed = true;
                message = "Driver indexed";
            } catch (Exception ex) {
                message = "Index failed: " + ex.getMessage();
                log.warn("indexDriver failed for {}: {}", driverId, ex.getMessage());
            }
          }

        response.put("indexed", indexed);
        response.put("message", message);
        publish("INDEXED", driver.getId(), Map.of("source", source, "indexed", String.valueOf(indexed)));
        return response;
   }
    // ── S2-F12: Driver Performance Dashboard ─────────────────────
    public DriverDashboardDTO getDriverDashboard(Long driverId) {

        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));

        // M3: replace SQL JOIN with Feign call to ride-service
        DriverRideSummaryDTO summary;
        try {
            summary = rideServiceClient.getDriverRideSummary(driverId, null, null);
        } catch (FeignException.NotFound e) {
            summary = DriverRideSummaryDTO.empty();
        } catch (FeignException e) {
            log.warn("ride-service unavailable for dashboard {}: {}", driverId, e.getMessage());
            summary = DriverRideSummaryDTO.empty();
        }

        publish("DASHBOARD_VIEWED", driverId, Map.of());

        return DriverDashboardDTO.builder()
                .driverId(driver.getId())
                .name(driver.getName())
                .totalRides(summary.totalRides())
                .totalEarnings(summary.totalEarnings())
                .averageRideFare(summary.averageFare())
                .averageRating(driver.getRating())
                .totalRatings(driver.getTotalRatings())
                .build();
    }

    private List<DriverSearchResultDTO> fallbackFullTextSearch(
            String query, String vehicleType, String status,
            Double minRating, Double maxRating) {

        String lq = query == null ? "" : query.toLowerCase();

        return driverRepository.findAll().stream()
                .filter(d -> {
                    boolean nameMatch = d.getName() != null &&
                            d.getName().toLowerCase().contains(lq);
                    boolean descMatch = false;
                    if (d.getVehicleDetails() != null) {
                        Object desc = d.getVehicleDetails().get("description");
                        if (desc != null) descMatch = desc.toString().toLowerCase().contains(lq);
                    }
                    return nameMatch || descMatch;
                })
                .filter(d -> {
                    if (vehicleType == null || vehicleType.isBlank()) return true;
                    if (d.getVehicleDetails() == null) return false;
                    Object vt = d.getVehicleDetails().get("vehicleType");
                    return vt != null && vehicleType.equalsIgnoreCase(vt.toString());
                })
                .filter(d -> status == null || status.isBlank() ||
                        (d.getStatus() != null && d.getStatus().name().equalsIgnoreCase(status)))
                .filter(d -> minRating == null ||
                        (d.getRating() != null && d.getRating() >= minRating))
                .filter(d -> maxRating == null ||
                        (d.getRating() != null && d.getRating() <= maxRating))
                .map(elasticsearchHitAdapter::adaptFromDriver)
                .collect(Collectors.toList());
    }

    // ── S2-F2: Update Vehicle Details ────────────────────────────

    public Driver updateVehicleDetails(Driver driver, Map<String, Object> vehicleDetails) {
        Map<String, Object> existing = driver.getVehicleDetails();
        if (existing == null) existing = new HashMap<>();
        if (vehicleDetails != null) existing.putAll(vehicleDetails);
        driver.setVehicleDetails(existing);
        Driver saved = driverRepository.save(driver);
        publish("VEHICLE_DETAILS_UPDATED", saved.getId(), Map.of());
        return saved;
    }

    // ── S2-F3: Driver Earnings ───────────────────────────────────

    public DriverEarningsDTO getDriverEarnings(Long driverId, LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate cannot be after endDate");
        }
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));

        String start = startDate != null ? startDate.toLocalDate().toString() : null;
        String end   = endDate   != null ? endDate.toLocalDate().toString()   : null;

        DriverRideSummaryDTO summary;
        try {
            summary = rideServiceClient.getDriverRideSummary(driverId, start, end);
        } catch (FeignException.NotFound e) {
            summary = DriverRideSummaryDTO.empty();
        } catch (FeignException e) {
            log.warn("ride-service unavailable for getDriverEarnings({}): {}", driverId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ride service unavailable");
        }
        return DriverEarningsDTO.builder()
                .driverId(driver.getId())
                .driverName(driver.getName())
                .totalRides(summary.totalRides())
                .totalEarnings(summary.totalEarnings())
                .averageEarningsPerRide(summary.averageFare())
                .build();
    }

    // ── S2-F4: Update Driver Availability ───────────────────────

    public boolean hasActiveRides(Long driverId) {
        try {
            int count = rideServiceClient.getDriverActiveRideCount(driverId);
            return count > 0;
        } catch (FeignException.NotFound e) {
            return false;
        } catch (FeignException e) {
            log.warn("ride-service unavailable for hasActiveRides({}): {}", driverId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ride service unavailable");
        }
    }

    @Transactional
    public Driver updateAvailability(Driver driver, AvailabilityUpdateRequest request) {
        boolean goingOffline = request.getStatus() == DriverStatus.OFFLINE;
        String lockKey = "driver::lock::" + driver.getId();
        String token = goingOffline
                ? redisLockService.tryAcquire(lockKey, java.time.Duration.ofSeconds(10))
                : null;
        if (goingOffline && token == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Driver " + driver.getId() + " is locked by another request");
        }
        try {
            String oldStatus = driver.getStatus() != null ? driver.getStatus().name() : null;
            driver.setStatus(request.getStatus());
            Driver saved = driverRepository.save(driver);
            publish("AVAILABILITY_UPDATED", saved.getId(),
                    Map.of("status", String.valueOf(saved.getStatus())));
            driverEventPublisher.publishStatusChanged(
                    saved.getId(), oldStatus, saved.getStatus().name());
            return saved;
        } finally {
            if (token != null) {
                redisLockService.release(lockKey, token);
            }
        }
    }

    // ── S2-F5: Filter by Vehicle Type ────────────────────────────

    @org.springframework.cache.annotation.Cacheable(value = "S2-F5",
            key = "T(java.util.Objects).toString(#type,'null') + ':' + T(java.util.Objects).toString(#status,'null')")
    public List<Long> getDriverIdsByVehicleType(String type, DriverStatus status) {
        if (type == null || type.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vehicle type is required");
        }
        try {
            VehicleType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid vehicle type: " + type);
        }
        return driverRepository
                .findByVehicleTypeAndOptionalStatus(type, status != null ? status.name() : null)
                .stream().map(Driver::getId).toList();
    }

    public List<Driver> getDriversByVehicleType(String type, DriverStatus status) {
        List<Long> ids = (self != null ? self : this).getDriverIdsByVehicleType(type, status);
        if (ids.isEmpty()) return new ArrayList<>();
        return driverRepository.findAllById(ids);
    }

    // ── S2-F6: Top Rated Drivers ─────────────────────────────────

    public List<TopDriverDTO> getTopRatedDrivers(int limit) {
        List<Object[]> results = driverRepository.findTopRatedDrivers(limit);
        List<TopDriverDTO> topDrivers = new ArrayList<>();
        for (Object[] row : results) {
            topDrivers.add(TopDriverDTO.builder()
        // .driverId(((Number) row[0]).longValue())
        // .name((String) row[1])
        // .rating(((Number) row[2]).doubleValue())
        // .totalRides(((Number) row[3]).longValue())
                .driverId(((Number) row[0]).longValue())
                .name((String) row[1])
                .rating(((Number) row[2]).doubleValue())
                .totalRides(((Number) row[3]).longValue())
                .build());
        }
        
        return topDrivers;
    }

    // ── S2-F7: Rate a Driver ─────────────────────────────────────

    public boolean rideExists(Long rideId) {
        return rideNativeRepository.rideExists(rideId);
    }

    public boolean isCompletedRideForDriver(Long rideId, Long driverId) {
        return rideNativeRepository.isCompletedRideForDriver(rideId, driverId);
    }

    /**
     * Validates the ride via Feign → ride-service and rates the driver.
     * M3: ride validation no longer queries the shared rides table directly.
     */
    @Transactional
    public Driver rateDriver(Driver driver, DriverRatingRequest request) {
        RideDTO ride;
        try {
            ride = rideServiceClient.getRide(request.getRideId());
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found");
        } catch (FeignException e) {
            log.warn("ride-service unavailable for getRide({}): {}", request.getRideId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Ride service unavailable");
        }

        if (!driver.getId().equals(ride.driverId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride does not belong to this driver");
        }
        if (!"COMPLETED".equals(ride.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride must be COMPLETED to rate the driver");
        }

        int updated = driverRepository.applyRating(driver.getId(), request.getRating());
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found");
        }
        Driver saved = driverRepository.findById(driver.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));
        publish("RATING_RECORDED", saved.getId(), Map.of());
        driverEventPublisher.publishDriverRated(
                saved.getId(), request.getRideId(), Double.valueOf(request.getRating()), null);
        return saved;
    }

    @Transactional
    public void verifyDriverDocument(Long driverId, Long documentId, Long callerId) {

        // Local validations FIRST (no Feign call needed)
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found"));

        DriverDocument document = driverDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        if (!document.getDriver().getId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document does not belong to this driver");
        }
        if (document.getExpiryDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document is expired");
        }

        // Feign call AFTER local validations pass
        try {
            UserDTO caller = userServiceClient.getUser(callerId);
            if (!caller.role().equals("ADMIN")) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verifier is not an admin");
            }
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verifier user not found");
        } catch (FeignException e) {
            log.warn("user-service unavailable for caller {}: {}", callerId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable");
        }

        // Update document
        document.setVerified(true);
        Map<String, Object> metadata = document.getMetadata();
        if (metadata == null) metadata = new HashMap<>();
        metadata.put("verifiedAt", LocalDateTime.now().toString());
        metadata.put("verifiedBy", callerId);
        document.setMetadata(metadata);
        driverDocumentRepository.save(document);

        // Publish RabbitMQ event
        driverEventPublisher.publishDocumentVerified(driverId, documentId, callerId);
    }
    // ── S2-F9: Drivers with Expired Documents ────────────────────

    public List<DriverDocumentAlertDTO> getDriversWithExpiredDocuments() {
        return driverRepository.findAll().stream()
                .filter(driver -> driver.getDriverDocuments() != null &&
                        driver.getDriverDocuments().stream()
                                .anyMatch(doc -> doc.getExpiryDate().isBefore(LocalDate.now())))
                .map(driver -> {
                    List<DocumentType> expired = driver.getDriverDocuments().stream()
                            .filter(doc -> doc.getExpiryDate().isBefore(LocalDate.now()))
                            .map(DriverDocument::getType)
                            .collect(Collectors.toList());
                    // return new DriverExpiredDocument(
                    //         driver.getId(), driver.getName(), driver.getStatus(), expired);
                    return DriverDocumentAlertDTO.builder()
                
                    .driverId(driver.getId())
                    .driverName(driver.getName())
                    .status(driver.getStatus())
                    .expiredDocuments(expired)
                    .expiredCount(expired.size())
                    .build();
                })
                .collect(Collectors.toList());
    }
    

    // ── Shared helpers ────────────────────────────────────────────

    public Optional<Driver> getDriverById(Long id) {
        return driverRepository.findById(id);
    }

    public Double getDriverRating(Long driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found with id: " + driverId))
                .getRating();
    }

    public Driver updateDriverStatus(Long driverId, DriverStatus status) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found with id: " + driverId));
        driver.setStatus(status);
        return driverRepository.save(driver);
    }
}