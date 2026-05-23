package com.team21.uber.ride.service;

import java.time.LocalDate;

import com.team21.uber.contracts.events.RideCancelledEvent;
import com.team21.uber.ride.dto.AddStopRequest;
import com.team21.uber.ride.dto.RideAnalyticsDTO;
import com.team21.uber.ride.dto.FareEstimateDTO;
import com.team21.uber.ride.dto.FareEstimateRequest;
import com.team21.uber.ride.dto.RideDetailsDTO;
import com.team21.uber.ride.model.Ride;
import com.team21.uber.ride.model.RideStatus;
import com.team21.uber.ride.model.RideStop;
import com.team21.uber.ride.model.RideStopStatus;
import com.team21.uber.ride.repository.RideRepository;
import com.team21.uber.ride.repository.RideStopRepository;
import com.team21.uber.ride.repository.RideEventRepository;
import com.team21.uber.ride.repository.UserNodeRepository;
import com.team21.uber.ride.messaging.publishers.RideEventPublisher;
import com.team21.uber.ride.saga.SagaTriggerService;
import com.team21.uber.contracts.dto.DriverDTO;
import com.team21.uber.contracts.dto.UserDTO;
import com.team21.uber.contracts.events.RidePlacedEvent;
import com.team21.uber.contracts.feign.DriverServiceClient;
import com.team21.uber.contracts.feign.UserServiceClient;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
import com.team21.uber.ride.observer.EntityObserver;
import com.team21.uber.ride.observer.MongoEventLogger;
import com.team21.uber.ride.event.EventType;
import com.team21.uber.ride.cache.CacheService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import com.team21.uber.ride.dto.RideSummaryDTO;
import com.team21.uber.ride.dto.DriverRideSummaryDTO;


@Service
public class RideService {

    private static final Logger log = LoggerFactory.getLogger(RideService.class);

    private final RideRepository rideRepository;
    private final RideStopRepository rideStopRepository;
    private final UserNodeRepository userNodeRepository;
    private final CacheService cacheService;
    private final org.springframework.beans.factory.ObjectProvider<Neo4jClient> neo4jClientProvider;
    private final org.springframework.beans.factory.ObjectProvider<Driver> neo4jDriverProvider;
    private final DriverServiceClient driverServiceClient;
    private final RideEventPublisher rideEventPublisher;
    private final UserServiceClient userServiceClient;
    private final SagaTriggerService sagaTriggerService;  // S3-F4 saga pre-checks + publish
    private final com.team21.uber.contracts.lock.RedisLockService redisLockService;

    private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();

    public RideService(RideRepository rideRepository,
                       RideStopRepository rideStopRepository,
                       UserNodeRepository userNodeRepository,
                       RideEventRepository rideEventRepository,
                       CacheService cacheService,
                       org.springframework.beans.factory.ObjectProvider<Neo4jClient> neo4jClientProvider,
                       org.springframework.beans.factory.ObjectProvider<Driver> neo4jDriverProvider,
                       DriverServiceClient driverServiceClient,
                       RideEventPublisher rideEventPublisher,
                       UserServiceClient userServiceClient,
                       SagaTriggerService sagaTriggerService,
                       com.team21.uber.contracts.lock.RedisLockService redisLockService) {
        this.rideRepository = rideRepository;
        this.rideStopRepository = rideStopRepository;
        this.userNodeRepository = userNodeRepository;
        this.cacheService = cacheService;
        this.neo4jClientProvider = neo4jClientProvider;
        this.neo4jDriverProvider = neo4jDriverProvider;
        this.driverServiceClient = driverServiceClient;
        this.rideEventPublisher = rideEventPublisher;
        this.userServiceClient = userServiceClient;
        this.sagaTriggerService = sagaTriggerService;
        this.redisLockService = redisLockService;

        MongoEventLogger logger = new MongoEventLogger(rideEventRepository, EventType.RIDE);
        registerObserver(logger);
    }

    public void registerObserver(EntityObserver observer) {
        observers.add(observer);
    }

    public void unregisterObserver(EntityObserver observer) {
        observers.remove(observer);
    }

    protected void notifyObservers(String eventType, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(eventType, payload);
        }
    }

    public Ride updateRideStatus(Long id, String status) {
        Ride ride = rideRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));
        try {
            ride.setStatus(RideStatus.valueOf(status));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ride status");
        }
        return rideRepository.save(ride);
    }

    // S3-F1
    public List<Ride> searchRides(String status, Long userId, Long driverId,
                                  LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : startDate.atStartOfDay();
        LocalDateTime end = endDate == null ? LocalDateTime.now().plusYears(10).withHour(23).withMinute(59).withSecond(59) : endDate.atTime(23, 59, 59);
        RideStatus rideStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                rideStatus = RideStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + status);
            }
        }
        List<Ride> base;
        if (rideStatus != null) {
            base = rideRepository.findByStatusAndRequestedAtBetweenOrderByRequestedAtDesc(rideStatus, start, end);
        } else {
            base = rideRepository.findByRequestedAtBetweenOrderByRequestedAtDesc(start, end);
        }
        return base.stream()
                .filter(r -> userId == null || (r.getUserId() != null && r.getUserId().equals(userId)))
                .filter(r -> driverId == null || (r.getDriverId() != null && r.getDriverId().equals(driverId)))
                .toList();
    }

    // ── S3-F2: Assign Driver (Feign + RabbitMQ + Redis lock) ─────────────────
    @Transactional
    public Ride assignDriver(Long rideId, Long driverId) {
        String lockKey = "driver::lock::" + driverId;
        String token = redisLockService.tryAcquire(lockKey, java.time.Duration.ofSeconds(10));
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Driver " + driverId + " is being assigned by another request");
        }
        try {
            Ride ride = rideRepository.findById(rideId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

            if (ride.getStatus() != RideStatus.REQUESTED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ride is not in REQUESTED status");
            }

            DriverDTO driver;
            try {
                log.info("Calling DriverServiceClient.getDriver with driverId={}", driverId);
                driver = driverServiceClient.getDriver(driverId);
            } catch (FeignException.NotFound e) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found");
            } catch (FeignException e) {
                log.error("Feign call to driver-service failed for driverId={}: {}", driverId, e.getMessage());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Driver service temporarily unavailable");
            }

            if (!"AVAILABLE".equals(driver.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver is not available");
            }

            ride.setDriverId(driverId);
            ride.setStatus(RideStatus.ACCEPTED);
            Ride saved = rideRepository.save(ride);

            rideEventPublisher.publishRidePlaced(
                    new RidePlacedEvent(saved.getId(), saved.getUserId(), driverId)
            );

            Map<String, Object> eventPayload = new HashMap<>();
            eventPayload.put("rideId", saved.getId());
            Map<String, Object> details = new HashMap<>();
            details.put("rideId", saved.getId());
            details.put("driverId", driverId);
            details.put("userId", saved.getUserId());
            details.put("status", "ACCEPTED");
            eventPayload.put("details", details);
            notifyObservers("DRIVER_ASSIGNED", eventPayload);

            return saved;
        } finally {
            redisLockService.release(lockKey, token);
        }
    }

    // S3-F3
    public FareEstimateDTO getFareEstimate(FareEstimateRequest request) {
        if (request == null
                || request.getPickupLatitude() == null || request.getPickupLongitude() == null
                || request.getDropoffLatitude() == null || request.getDropoffLongitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "pickup and dropoff coordinates are required");
        }
        double dLat = request.getDropoffLatitude() - request.getPickupLatitude();
        double dLon = request.getDropoffLongitude() - request.getPickupLongitude();
        double distance = Math.sqrt(dLat * dLat + dLon * dLon) * 111;
        double duration = (distance / 40) * 60;
        int nearbyCount = rideRepository.countNearbyActiveRides(
                request.getPickupLatitude() - 0.01, request.getPickupLatitude() + 0.01,
                request.getPickupLongitude() - 0.01, request.getPickupLongitude() + 0.01);
        double surgeMultiplier;
        if (nearbyCount <= 10) surgeMultiplier = 1.0;
        else if (nearbyCount <= 20) surgeMultiplier = 1.5;
        else surgeMultiplier = 2.0;
        double fare = 15.0 * distance * surgeMultiplier;
        return new FareEstimateDTO(
                Math.round(distance * 10.0) / 10.0,
                Math.round(duration * 10.0) / 10.0,
                Math.round(fare * 10.0) / 10.0,
                surgeMultiplier);
    }

    // ── S3-F4: Complete Ride ──────────────────────────────────────────────────
    // Saga pre-checks (user ACTIVE, driver BUSY, GPS ping) + publish ride.completed
    // are now the responsibility of SagaTriggerService (saga/ package per §12 spec).
    @Transactional
    public Ride completeRide(Long id) {
        // 1. Find ride → 404 if not found
        Ride ride = rideRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        // 2. Validate status = IN_PROGRESS → 400 if not
        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride must be IN_PROGRESS to complete. Current status: " + ride.getStatus());
        }

        if (ride.getDriverId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride has no assigned driver — cannot complete");
        }

        // 3. Calculate fare if unset (local — no cross-service call)
        if (ride.getFare() == null) {
            double distance = Math.sqrt(
                    Math.pow(ride.getDropoffLatitude() - ride.getPickupLatitude(), 2) +
                            Math.pow(ride.getDropoffLongitude() - ride.getPickupLongitude(), 2)) * 111;
            ride.setFare(Math.round(15.0 * distance * 100.0) / 100.0);
        }

        // 4. Prepare the entity — status + timestamp (not yet persisted)
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());

        // 5. Delegate to SagaTriggerService:
        //    → runs 3 Feign pre-checks (throws 400/503 on failure — ride stays IN_PROGRESS)
        //    → saves ride as COMPLETED
        //    → publishes ride.completed to trigger payment + driver sagas
        return sagaTriggerService.triggerRideCompletion(ride);
    }

    // S3-F5
    public List<Ride> filterByMetadata(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Key and value must not be blank");
        }
        return rideRepository.findByMetadataField(key, value);
    }

    // S3-F6
    public RideAnalyticsDTO getRideAnalytics(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = rideRepository.getRideAnalytics(startDate, endDate);
        Object[] result = results.get(0);

        long totalRides = ((Number) result[0]).longValue();
        long completedRides = ((Number) result[1]).longValue();
        long cancelledRides = ((Number) result[2]).longValue();
        double totalRevenue = ((Number) result[3]).doubleValue();
        double averageFare = ((Number) result[4]).doubleValue();
        double completionRate = totalRides > 0 ?
                ((double) completedRides / totalRides) : 0.0;

        return new RideAnalyticsDTO.Builder()
                .totalRides(totalRides)
                .completedRides(completedRides)
                .cancelledRides(cancelledRides)
                .totalRevenue(totalRevenue)
                .averageFare(averageFare)
                .completionRate(completionRate)
                .build();
    }

    // S3-F7
    @Transactional
    public void cancelRide(Long id) {
        Ride ride = rideRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (ride.getStatus() != RideStatus.REQUESTED && ride.getStatus() != RideStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only REQUESTED or ACCEPTED rides can be cancelled. Current status: " + ride.getStatus());
        }

        ride.setStatus(RideStatus.CANCELLED);
        Ride saved = rideRepository.save(ride);
        log.info("S3-F7: Ride {} marked CANCELLED", id);

        rideEventPublisher.publishRideCancelled(
                new RideCancelledEvent(saved.getId(), saved.getUserId(), saved.getDriverId(), "user_requested")
        );
        log.info("S3-F7: Published ride.cancelled for ride {} reason=user_requested", id);
    }

    // S3-F8
    @Transactional
    public Ride addStops(Long rideId, List<AddStopRequest> stopRequests) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));
        if (ride.getStatus() != RideStatus.REQUESTED && ride.getStatus() != RideStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot add stops to a ride that is not REQUESTED or ACCEPTED");
        }
        for (AddStopRequest req : stopRequests) {
            if (req.getLatitude() == null || req.getLongitude() == null
                    || req.getAddress() == null || req.getAddress().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Each stop must have latitude, longitude, and address");
            }
        }
        int maxOrder = rideStopRepository.findMaxStopOrderByRideId(rideId);
        List<RideStop> newStops = new ArrayList<>();
        for (AddStopRequest req : stopRequests) {
            maxOrder++;
            RideStop stop = new RideStop();
            stop.setLatitude(req.getLatitude());
            stop.setLongitude(req.getLongitude());
            stop.setAddress(req.getAddress());
            stop.setMetadata(req.getMetadata());
            stop.setStopOrder(maxOrder);
            stop.setStatus(RideStopStatus.PENDING);
            stop.setRide(ride);
            newStops.add(stop);
        }
        rideStopRepository.saveAll(newStops);
        return rideRepository.findById(rideId).get();
    }

    // S3-F9
    public RideDetailsDTO getRideDetails(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));
        List<RideStop> stops = rideStopRepository.findByRideIdOrderByStopOrderAsc(rideId);
        int totalStops = stops.size();
        long completedStops = stops.stream()
                .filter(s -> s.getStatus() == RideStopStatus.REACHED)
                .count();
        return new RideDetailsDTO(
                ride.getId(),
                ride.getUserId(),
                ride.getDriverId(),
                ride.getStatus(),
                ride.getFare(),
                ride.getMetadata(),
                stops,
                totalStops,
                completedStops);
    }

    // ── S3-F11: Record User-Driver Riding Pattern ─────────────────────────────
    public Map<String, Object> recordInteraction(Long rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only COMPLETED rides can record interactions. Current status: " + ride.getStatus());
        }

        Long userId = ride.getUserId();
        Long driverId = ride.getDriverId();

        if (driverId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride has no assigned driver — cannot record interaction");
        }

        Driver idempDriver = neo4jDriverProvider.getIfAvailable();
        if (idempDriver == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Neo4j unavailable");
        }

        boolean alreadyRecorded = false;
        long currentCount = 0L;
        try (Session session = idempDriver.session()) {
            Result r = session.run(
                    "MATCH (u:User {userId: $userId})-[rel:RODE_WITH]->(d:Driver {driverId: $driverId}) " +
                            "RETURN coalesce(rel.rideCount, 0) AS rc, $rideId IN coalesce(rel.recordedRideIds, []) AS recorded",
                    Map.of("userId", userId, "driverId", driverId, "rideId", rideId));
            if (r.hasNext()) {
                Record rec = r.next();
                currentCount = rec.get("rc").asLong(0L);
                alreadyRecorded = rec.get("recorded").asBoolean(false);
            }
        } catch (Exception ignored) {}

        if (alreadyRecorded) {
            Map<String, Object> result = new HashMap<>();
            result.put("message", "Interaction already recorded for this ride");
            result.put("rideId", rideId);
            result.put("rideCount", currentCount);
            return result;
        }

        log.info("Calling UserServiceClient.getUser with userId={}", userId);
        UserDTO userDTO;
        try {
            userDTO = userServiceClient.getUser(userId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        } catch (FeignException e) {
            log.error("Feign call to user-service failed for userId={}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "User service temporarily unavailable");
        }

        log.info("Calling DriverServiceClient.getDriver with driverId={}", driverId);
        DriverDTO driverDTO;
        try {
            driverDTO = driverServiceClient.getDriver(driverId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver not found");
        } catch (FeignException e) {
            log.error("Feign call to driver-service failed for driverId={}: {}", driverId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Driver service temporarily unavailable");
        }

        String userName = userDTO.name();
        String driverName = driverDTO.name();
        String vehicleType = (driverDTO.vehicleDetails() != null)
                ? (String) driverDTO.vehicleDetails().getOrDefault("vehicleType", "UNKNOWN")
                : "UNKNOWN";

        int rideCount = 1;
        Driver driver = neo4jDriverProvider.getIfAvailable();
        if (driver == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Neo4j driver unavailable");
        }
        try (Session session = driver.session(org.neo4j.driver.SessionConfig.forDatabase("neo4j"))) {
            String cypher =
                    "MERGE (u:User {userId: $userId}) " +
                            "ON CREATE SET u.name = $userName " +
                            "MERGE (d:Driver {driverId: $driverId}) " +
                            "ON CREATE SET d.name = $driverName, d.vehicleType = $vehicleType " +
                            "MERGE (u)-[r:RODE_WITH]->(d) " +
                            "ON CREATE SET r.rideCount = 1, r.lastRideDate = $now, r.recordedRideIds = [$rideId] " +
                            "ON MATCH SET r.rideCount = COALESCE(r.rideCount, 0) + 1, r.lastRideDate = $now, " +
                            "            r.recordedRideIds = COALESCE(r.recordedRideIds, []) + $rideId " +
                            "RETURN r.rideCount AS rc";
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            params.put("userName", userName);
            params.put("driverId", driverId);
            params.put("driverName", driverName);
            params.put("vehicleType", vehicleType);
            params.put("rideId", rideId);
            params.put("now", LocalDateTime.now());
            Result result = session.run(cypher, params);
            if (result.hasNext()) {
                Record rec = result.next();
                Object rc = rec.get("rc").asObject();
                if (rc instanceof Number n) rideCount = n.intValue();
            }
            result.consume();
        } catch (Exception ex) {
            log.error("Neo4j mergeRodeWith failed for user={} driver={} ride={}: {}",
                    userId, driverId, rideId, ex.toString());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Neo4j write failed: " + ex.getMessage());
        }

        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("rideId", rideId);
        Map<String, Object> details = new HashMap<>();
        details.put("rideId", rideId);
        details.put("userId", userId);
        details.put("driverId", driverId);
        details.put("rideCount", rideCount);
        eventPayload.put("details", details);
        notifyObservers("INTERACTION_RECORDED", eventPayload);

        cacheService.evictByPattern("ride-service::S3-F12::*");

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("message", "Interaction recorded successfully");
        resultMap.put("rideId", rideId);
        resultMap.put("userId", userId);
        resultMap.put("driverId", driverId);
        resultMap.put("rideCount", rideCount);
        return resultMap;
    }

    // ── S3-F12: Recommendations ───────────────────────────────────────────────
    public List<Map<String, Object>> getRecommendations(Long userId, int limit) {
        log.info("Calling UserServiceClient.getUser for recommendations userId={}", userId);
        try {
            userServiceClient.getUser(userId);
        } catch (FeignException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + userId);
        } catch (FeignException e) {
            log.error("Feign call to user-service failed for userId={}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "User service temporarily unavailable");
        }

        Neo4jClient neo4j = neo4jClientProvider.getIfAvailable();
        if (neo4j == null) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> candidates;
        try {
            java.util.Collection<Map<String, Object>> rows = neo4j.query(
                            "MATCH (u:User {userId: $userId})-[:RODE_WITH]->(d:Driver) " +
                                    "WITH u, collect(d.driverId) AS userDrivers " +
                                    "MATCH (other:User)-[:RODE_WITH]->(d2:Driver) " +
                                    "WHERE other.userId <> $userId AND d2.driverId IN userDrivers " +
                                    "WITH u, other, userDrivers " +
                                    "MATCH (other)-[r2:RODE_WITH]->(rec:Driver) " +
                                    "WHERE NOT rec.driverId IN userDrivers " +
                                    "WITH rec, sum(r2.rideCount) AS score " +
                                    "RETURN rec.driverId AS driverId, score " +
                                    "ORDER BY score DESC LIMIT $limit")
                    .bind(userId).to("userId")
                    .bind((long) Math.min(limit, 100)).to("limit")
                    .fetch()
                    .all();
            candidates = new ArrayList<>(rows);
        } catch (Exception ex) {
            log.warn("Recommendations Neo4j query failed for user={}: {}", userId, ex.toString());
            return new ArrayList<>();
        }

        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            Object did = candidate.get("driverId");
            Long candidateDriverId = (did instanceof Number n) ? n.longValue() : null;
            if (candidateDriverId == null) continue;

            Object sc = candidate.get("score");
            long score = (sc instanceof Number n) ? n.longValue() : 0L;

            try {
                DriverDTO driverDTO = driverServiceClient.getDriver(candidateDriverId);
                String vehicleType = (driverDTO.vehicleDetails() != null)
                        ? (String) driverDTO.vehicleDetails().getOrDefault("vehicleType", "UNKNOWN")
                        : "UNKNOWN";

                Map<String, Object> dto = new HashMap<>();
                dto.put("driverId", candidateDriverId);
                dto.put("name", driverDTO.name());
                dto.put("vehicleType", vehicleType);
                dto.put("score", score);
                enriched.add(dto);
            } catch (FeignException.NotFound e) {
                log.warn("Driver {} no longer exists in driver-service, skipping recommendation",
                        candidateDriverId);
            } catch (FeignException e) {
                log.warn("driver-service unavailable for driverId={}: {}", candidateDriverId, e.getMessage());
                Map<String, Object> dto = new HashMap<>();
                dto.put("driverId", candidateDriverId);
                dto.put("name", "Unknown");
                dto.put("vehicleType", "UNKNOWN");
                dto.put("score", score);
                enriched.add(dto);
            }
        }

        return enriched;
    }

// ══════════════════════════════════════════════════════════════
//  M3 Internal methods — serve the 6 Feign-facing endpoints
//  All read from ride-postgres via existing repository methods
//  No Feign calls here — ride-service owns this data
// ══════════════════════════════════════════════════════════════

    // Called by user-service S1-F3 via Feign
    public RideSummaryDTO getUserRideSummary(Long userId) {
        log.info("Building ride summary for userId={}", userId);

        // Uses existing getUserRideSummary native query
        List<Object[]> rows = rideRepository.getUserRideSummary(userId);
        if (rows.isEmpty()) {
            return RideSummaryDTO.builder()
                    .userId(userId).totalRides(0).completedRides(0)
                    .cancelledRides(0).totalSpent(0.0).averageFare(0.0)
                    .build();
        }
        Object[] r = rows.get(0);
        return RideSummaryDTO.builder()
                .userId(userId)
                .totalRides(((Number) r[0]).longValue())
                .completedRides(((Number) r[1]).longValue())
                .cancelledRides(((Number) r[2]).longValue())
                .totalSpent(((Number) r[3]).doubleValue())
                .averageFare(((Number) r[4]).doubleValue())
                .build();
    }

    // Called by user-service S1-F4 via Feign
// Active = statuses that block deactivation per spec §3
    public int getActiveRideCountForUser(Long userId) {
        log.info("Counting active rides for userId={}", userId);
        long count = rideRepository.countByUserIdAndStatusIn(
                userId,
                List.of(
                        RideStatus.REQUESTED,
                        RideStatus.ACCEPTED,
                        RideStatus.IN_PROGRESS,
                        RideStatus.COMPLETED,
                        RideStatus.PAYMENT_PENDING
                )
        );
        return (int) count;
    }

    // Called by user-service S1-F9 via Feign
// Truly completed = COMPLETED or PAID per spec §3
    public long getCompletedRideCountForUser(Long userId) {
        log.info("Counting completed rides for userId={}", userId);
        return rideRepository.countByUserIdAndStatusIn(
                userId,
                List.of(RideStatus.COMPLETED, RideStatus.PAID)
        );
    }

    // Called by driver-service S2-F3 and S2-F12 via Feign
    public DriverRideSummaryDTO getDriverRideSummary(Long driverId,
                                                     String startDate,
                                                     String endDate) {
        log.info("Building driver ride summary for driverId={} start={} end={}",
                driverId, startDate, endDate);

        // Parse date strings or use sentinel values when not provided
        // Your existing repo query uses completed_at BETWEEN start AND end
        LocalDateTime start = (startDate != null && !startDate.isBlank())
                ? LocalDate.parse(startDate).atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime end = (endDate != null && !endDate.isBlank())
                ? LocalDate.parse(endDate).atTime(23, 59, 59)
                : LocalDateTime.of(2099, 12, 31, 23, 59, 59);

        List<Object[]> rows = rideRepository.getDriverRideSummary(driverId, start, end);
        if (rows.isEmpty()) {
            return DriverRideSummaryDTO.builder()
                    .driverId(driverId).totalRides(0)
                    .totalEarnings(0.0).averageFare(0.0)
                    .build();
        }
        Object[] r = rows.get(0);
        return DriverRideSummaryDTO.builder()
                .driverId(driverId)
                .totalRides(((Number) r[0]).longValue())
                .totalEarnings(((Number) r[1]).doubleValue())
                .averageFare(((Number) r[2]).doubleValue())
                .build();
    }

    // Called by driver-service S2-F4 via Feign
// Active = statuses that block going OFFLINE per spec §5
    public int getActiveRideCountForDriver(Long driverId) {
        log.info("Counting active rides for driverId={}", driverId);
        long count = rideRepository.countByDriverIdAndStatusIn(
                driverId,
                List.of(
                        RideStatus.ACCEPTED,
                        RideStatus.IN_PROGRESS,
                        RideStatus.COMPLETED,
                        RideStatus.PAYMENT_PENDING
                )
        );
        return (int) count;
    }

    // Called by driver-service S2-F6 via Feign
    public long getCompletedRideCountForDriver(Long driverId) {
        log.info("Counting completed rides for driverId={}", driverId);
        return rideRepository.countByDriverIdAndStatusIn(
                driverId,
                List.of(RideStatus.COMPLETED, RideStatus.PAID)
        );
    }
    // ── Ride CRUD ─────────────────────────────────────────────────────────────

    public Ride createRide(Ride ride) {
        ride.setRequestedAt(LocalDateTime.now());
        ride.setStatus(RideStatus.REQUESTED);
        return rideRepository.save(ride);
    }

    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }

    public Ride getRideById(Long id) {
        return rideRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));
    }

    public Ride updateRide(Long id, Ride updated) {
        Ride ride = getRideById(id);
        ride.setUserId(updated.getUserId());
        ride.setDriverId(updated.getDriverId());
        ride.setPickupLatitude(updated.getPickupLatitude());
        ride.setPickupLongitude(updated.getPickupLongitude());
        ride.setDropoffLatitude(updated.getDropoffLatitude());
        ride.setDropoffLongitude(updated.getDropoffLongitude());
        ride.setStatus(updated.getStatus());
        ride.setFare(updated.getFare());
        ride.setMetadata(updated.getMetadata());
        ride.setCompletedAt(updated.getCompletedAt());
        return rideRepository.save(ride);
    }

    public void deleteRide(Long id) {
        getRideById(id);
        rideRepository.deleteById(id);
    }
}