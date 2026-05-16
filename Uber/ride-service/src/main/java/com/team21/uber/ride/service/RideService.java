package com.team21.uber.ride.service;

import java.time.LocalDate;
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
import com.team21.uber.ride.messaging.RideEventPublisher;
import com.team21.uber.contracts.dto.DriverDTO;
import com.team21.uber.contracts.dto.UserDTO;
import com.team21.uber.contracts.events.RidePlacedEvent;
import com.team21.uber.contracts.events.RideCancelledEvent;
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
                       UserServiceClient userServiceClient) {
        this.rideRepository = rideRepository;
        this.rideStopRepository = rideStopRepository;
        this.userNodeRepository = userNodeRepository;
        this.cacheService = cacheService;
        this.neo4jClientProvider = neo4jClientProvider;
        this.neo4jDriverProvider = neo4jDriverProvider;
        this.driverServiceClient = driverServiceClient;
        this.rideEventPublisher = rideEventPublisher;
        this.userServiceClient = userServiceClient;

        // Register the GoF Observer
        MongoEventLogger logger = new MongoEventLogger(rideEventRepository, EventType.RIDE);
        registerObserver(logger);
    }

    // --- Observer pattern methods ---
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

    // ── S3-F2: Assign Driver (M3 refactored — Feign + RabbitMQ) ──────────
    @Transactional
    public Ride assignDriver(Long rideId, Long driverId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ride is not in REQUESTED status");
        }

        DriverDTO driver;
        try {
            log.info("Calling DriverServiceClient.getDriver with driverId={}", driverId);
            driver = driverServiceClient.getDriver(driverId);
            log.info("DriverServiceClient.getDriver returned driver id={}, status={}", driver.id(), driver.status());
        } catch (FeignException.NotFound e) {
            log.warn("Driver not found via Feign for driverId={}", driverId);
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

    // S3-F4
    @Transactional
    public Ride completeRide(Long id) {
        Ride ride = rideRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));
        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride must be IN_PROGRESS to complete. Current status: " + ride.getStatus());
        }
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());
        if (ride.getFare() == null) {
            double distance = Math.sqrt(
                    Math.pow(ride.getDropoffLatitude() - ride.getPickupLatitude(), 2) +
                            Math.pow(ride.getDropoffLongitude() - ride.getPickupLongitude(), 2)) * 111;
            ride.setFare(Math.round(15.0 * distance * 100.0) / 100.0);
        }
        Ride saved = rideRepository.save(ride);
        if (saved.getDriverId() != null) {
            rideRepository.setDriverAvailable(saved.getDriverId());
        }
        rideRepository.createPendingPayment(saved.getId(), saved.getUserId(), saved.getFare());
        return saved;
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

    // S3-F7 — Cancel Ride (M3 refactored)
    // M3 change: Remove direct driver update. Publish ride.cancelled event.
    // Driver re-availability happens asynchronously when driver-service consumes the event.
    @Transactional
    public void cancelRide(Long id) {
        // 1. Find ride by ID → 404 if not found
        Ride ride = rideRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        // 2. Validate status IN (REQUESTED, ACCEPTED) → 400 if not
        if (ride.getStatus() != RideStatus.REQUESTED && ride.getStatus() != RideStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only REQUESTED or ACCEPTED rides can be cancelled. Current status: " + ride.getStatus());
        }

        // 3. Set ride status = CANCELLED
        ride.setStatus(RideStatus.CANCELLED);
        Ride saved = rideRepository.save(ride);
        log.info("S3-F7: Ride {} marked CANCELLED", id);

        // 4. Publish ride.cancelled to ride.events exchange
        // If driverId is null, the payload still contains it (null) — driver-service silently ignores
        rideEventPublisher.publishRideCancelled(
                new RideCancelledEvent(saved.getId(), saved.getUserId(), saved.getDriverId(), "user_requested")
        );
        log.info("S3-F7: Published ride.cancelled event for ride {} with reason=user_requested", id);

        // 5. Driver re-availability happens asynchronously — driver-service consumes ride.cancelled
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

    // ── S3-F11: Record User-Driver Riding Pattern (M3 refactored — Feign) ──
    public Map<String, Object> recordInteraction(Long rideId) {
        // b) Find ride by ID in PostgreSQL
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        // c) Verify ride status is COMPLETED
        if (ride.getStatus() != RideStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only COMPLETED rides can record interactions. Current status: " + ride.getStatus());
        }

        Long userId = ride.getUserId();
        Long driverId = ride.getDriverId();

        Driver idempDriver = neo4jDriverProvider.getIfAvailable();
        if (idempDriver == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Neo4j unavailable");
        }

        // d) Idempotency check — uses Neo4j only, no PG mutation
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

        // e) M3 change: Replace native SQL with Feign calls to user-service and driver-service
        log.info("Calling UserServiceClient.getUser with userId={}", userId);
        UserDTO userDTO;
        try {
            userDTO = userServiceClient.getUser(userId);
            log.info("UserServiceClient.getUser returned name={}", userDTO.name());
        } catch (FeignException.NotFound e) {
            log.warn("User not found via Feign for userId={}", userId);
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
            log.info("DriverServiceClient.getDriver returned name={}", driverDTO.name());
        } catch (FeignException.NotFound e) {
            log.warn("Driver not found via Feign for driverId={}", driverId);
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

        log.info("recordInteraction resolved userId={} userName={} driverId={} driverName={} vehicleType={}",
                userId, userName, driverId, driverName, vehicleType);

        // f) Merge nodes and relationship in Neo4j (raw Bolt Driver)
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

        // g) Log INTERACTION_RECORDED to MongoDB via Observer (non-idempotent path only)
        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("rideId", rideId);
        Map<String, Object> details = new HashMap<>();
        details.put("rideId", rideId);
        details.put("userId", userId);
        details.put("driverId", driverId);
        details.put("rideCount", rideCount);
        eventPayload.put("details", details);
        notifyObservers("INTERACTION_RECORDED", eventPayload);

        // Invalidate S3-F12 recommendation cache (wildcard per §4.4.4)
        cacheService.evictByPattern("ride-service::S3-F12::*");

        // h) Return 200 with confirmation
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("message", "Interaction recorded successfully");
        resultMap.put("rideId", rideId);
        resultMap.put("userId", userId);
        resultMap.put("driverId", driverId);
        resultMap.put("rideCount", rideCount);
        return resultMap;
    }

    // Used by S3-F12 controller for user existence check (still local SQL until S3-F12 is refactored)
    public String findUserName(Long userId) {
        try { return rideRepository.findUserNameById(userId); }
        catch (Exception e) { return null; }
    }

    public boolean userNodeExists(Long userId) {
        Driver d = neo4jDriverProvider.getIfAvailable();
        if (d == null) return false;
        try (Session s = d.session()) {
            Result r = s.run("MATCH (u:User {userId: $userId}) RETURN count(u) > 0 AS exists",
                    Map.of("userId", userId));
            if (r.hasNext()) return r.next().get("exists").asBoolean(false);
            return false;
        } catch (Exception e) { return false; }
    }

    // S3-F12: recommendations via Neo4j collaborative filtering
    public List<Map<String, Object>> getRecommendations(Long userId, int limit) {
        Neo4jClient neo4j = neo4jClientProvider.getIfAvailable();
        if (neo4j == null) {
            return new ArrayList<>();
        }
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
                                    "RETURN rec.driverId AS driverId, rec.name AS name, rec.vehicleType AS vehicleType, score " +
                                    "ORDER BY score DESC LIMIT $limit")
                    .bind(userId).to("userId")
                    .bind((long) limit).to("limit")
                    .fetch()
                    .all();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> dto = new HashMap<>();
                Object did = row.get("driverId");
                dto.put("driverId", did instanceof Number n ? n.longValue() : did);
                dto.put("name", row.get("name"));
                dto.put("vehicleType", row.get("vehicleType"));
                Object sc = row.get("score");
                dto.put("score", sc instanceof Number n ? n.longValue() : 0L);
                out.add(dto);
            }
            return out;
        } catch (Exception ex) {
            log.warn("Recommendations Neo4j query failed for user={}: {}", userId, ex.toString());
            return new ArrayList<>();
        }
    }

    // ── Ride CRUD ──────────────────────────────────────────

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
