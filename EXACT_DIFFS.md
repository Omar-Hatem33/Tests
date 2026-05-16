# EXACT CODE DIFFS FOR S3-F4 AND S3-F7

---

## SNIPPET 1: S3-F4 RideService.java

### Location: Import section (after line 20)
**Lines 20-23 in original file**

```diff
  import com.team21.uber.contracts.events.RidePlacedEvent;
+ import com.team21.uber.contracts.dto.LocationDTO;
+ import com.team21.uber.contracts.events.RideCompletedEvent;
  import com.team21.uber.contracts.feign.DriverServiceClient;
  import com.team21.uber.contracts.feign.UserServiceClient;
```

### Location: Field declaration (after line 61)
**Add after line 61: `private final UserServiceClient userServiceClient;`**

```diff
  private final RideEventPublisher rideEventPublisher;
  private final UserServiceClient userServiceClient;
+ private final LocationServiceClient locationServiceClient;

  private final List<EntityObserver> observers = new CopyOnWriteArrayList<>();
```

### Location: Constructor parameter (line 74)
**Add parameter in constructor signature**

```diff
  public RideService(RideRepository rideRepository,
                     RideStopRepository rideStopRepository,
                     UserNodeRepository userNodeRepository,
                     RideEventRepository rideEventRepository,
                     CacheService cacheService,
                     org.springframework.beans.factory.ObjectProvider<Neo4jClient> neo4jClientProvider,
                     org.springframework.beans.factory.ObjectProvider<Driver> neo4jDriverProvider,
                     DriverServiceClient driverServiceClient,
                     RideEventPublisher rideEventPublisher,
-                    UserServiceClient userServiceClient) {
+                    UserServiceClient userServiceClient,
+                    LocationServiceClient locationServiceClient) {
```

### Location: Constructor body (line 81)
**Add field assignment in constructor after `this.userServiceClient = userServiceClient;`**

```diff
      this.driverServiceClient = driverServiceClient;
      this.rideEventPublisher = rideEventPublisher;
      this.userServiceClient = userServiceClient;
+     this.locationServiceClient = locationServiceClient;

      // Register the GoF Observer
      MongoEventLogger logger = new MongoEventLogger(rideEventRepository, EventType.RIDE);
```

### Location: completeRide() method (line 218)
**REPLACE the entire completeRide() method starting at line 218**

**REMOVE (Original code):**
```java
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
```

**REPLACE WITH (New code):**
```java
    // S3-F4 — Complete Ride (Saga Trigger)
    // M3 refactor: Remove direct driver update and payment insert.
    // Run three Feign pre-checks, then publish ride.completed.
    @Transactional
    public Ride completeRide(Long id) {
        // 1. Find ride by ID → 404 if not found
        Ride ride = rideRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));

        // 2. Validate status = IN_PROGRESS → 400 if not
        if (ride.getStatus() != RideStatus.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ride must be IN_PROGRESS to complete. Current status: " + ride.getStatus());
        }

        Long userId = ride.getUserId();
        Long driverId = ride.getDriverId();

        // 3. Pre-saga Feign checks (all three must pass before any event is published)
        // Check 1: User service → user must be ACTIVE
        log.info("S3-F4 pre-check: Calling UserServiceClient.getUser with userId={}", userId);
        UserDTO userDTO;
        try {
            userDTO = userServiceClient.getUser(userId);
            if (!"ACTIVE".equals(userDTO.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "User must be ACTIVE to complete a ride. Current status: " + userDTO.status());
            }
            log.info("S3-F4 pre-check: User {} is ACTIVE", userId);
        } catch (FeignException.NotFound e) {
            log.warn("S3-F4 pre-check FAILED: User not found via Feign for userId={}", userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found");
        } catch (FeignException e) {
            log.error("S3-F4 pre-check FAILED: Feign call to user-service failed for userId={}: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "User service temporarily unavailable");
        }

        // Check 2: Driver service → driver status must be BUSY (proves assignment is active)
        log.info("S3-F4 pre-check: Calling DriverServiceClient.getDriver with driverId={}", driverId);
        DriverDTO driverDTO;
        try {
            driverDTO = driverServiceClient.getDriver(driverId);
            if (!"BUSY".equals(driverDTO.status())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Driver must be BUSY (actively assigned). Current status: " + driverDTO.status());
            }
            log.info("S3-F4 pre-check: Driver {} is BUSY", driverId);
        } catch (FeignException.NotFound e) {
            log.warn("S3-F4 pre-check FAILED: Driver not found via Feign for driverId={}", driverId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver not found");
        } catch (FeignException e) {
            log.error("S3-F4 pre-check FAILED: Feign call to driver-service failed for driverId={}: {}", driverId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Driver service temporarily unavailable");
        }

        // Check 3: Location service → driver must have a recent GPS ping (within last 5 minutes)
        log.info("S3-F4 pre-check: Calling LocationServiceClient.getRecentLocationForDriver with driverId={}", driverId);
        try {
            LocationDTO locationDTO = locationServiceClient.getRecentLocationForDriver(driverId);
            if (locationDTO == null || locationDTO.timestamp() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Driver location data not available");
            }
            LocalDateTime locationTime = locationDTO.timestamp();
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
            if (locationTime.isBefore(fiveMinutesAgo)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Driver not actively tracked (no recent location ping within 5 minutes)");
            }
            log.info("S3-F4 pre-check: Driver {} has recent location ping at {}", driverId, locationTime);
        } catch (FeignException.NotFound e) {
            log.warn("S3-F4 pre-check FAILED: No recent location found for driver {} (404)", driverId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Driver not actively tracked (no recent location ping)");
        } catch (ResponseStatusException e) {
            // Re-throw validation exceptions
            throw e;
        } catch (FeignException e) {
            log.error("S3-F4 pre-check FAILED: Feign call to location-service failed for driverId={}: {}", driverId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Location service temporarily unavailable");
        }

        // 4. All pre-checks passed. Calculate fare if unset using M1 surge-pricing formula
        if (ride.getFare() == null) {
            // Count nearby active rides (surge pricing logic from S3-F3)
            int nearbyCount = rideRepository.countNearbyActiveRides(
                    ride.getPickupLatitude() - 0.01, ride.getPickupLatitude() + 0.01,
                    ride.getPickupLongitude() - 0.01, ride.getPickupLongitude() + 0.01);
            double surgeMultiplier;
            if (nearbyCount <= 10) surgeMultiplier = 1.0;
            else if (nearbyCount <= 20) surgeMultiplier = 1.5;
            else surgeMultiplier = 2.0;

            double distance = Math.sqrt(
                    Math.pow(ride.getDropoffLatitude() - ride.getPickupLatitude(), 2) +
                            Math.pow(ride.getDropoffLongitude() - ride.getPickupLongitude(), 2)) * 111;
            double fare = 15.0 * distance * surgeMultiplier;
            ride.setFare(Math.round(fare * 100.0) / 100.0);
            log.info("S3-F4: Calculated fare for ride {}. Distance={}, surgeMultiplier={}, fare={}",
                    id, Math.round(distance * 10.0) / 10.0, surgeMultiplier, ride.getFare());
        }

        // 5. Mark ride status = COMPLETED, set completedAt = now(), save
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());
        Ride saved = rideRepository.save(ride);
        log.info("S3-F4: Ride {} marked COMPLETED with fare={}", id, saved.getFare());

        // 6. Publish ride.completed to ride.events exchange
        // NOTE: Transaction commits first (implicit by @Transactional), then event publishes
        rideEventPublisher.publishRideCompleted(
                new RideCompletedEvent(saved.getId(), saved.getUserId(), driverId, saved.getFare())
        );
        log.info("S3-F4: Published ride.completed event for ride {}", id);

        // 7. Return 200 with the updated ride
        return saved;
    }
```

---

## SNIPPET 2: S3-F4 RideRepository.java

### Location: S3-F4 section (around line 61-73 in original file)

**REMOVE (Original code):**
```java
    // ── S3-F4 ──────────────────────────────────────────

    @Modifying
    @Query(value = "UPDATE drivers SET status = 'AVAILABLE' WHERE id = :driverId", nativeQuery = true)
    void setDriverAvailable(@Param("driverId") Long driverId);

    @Modifying
    @Query(value = """
    INSERT INTO payments (ride_id, user_id, amount, method, status, transaction_details, created_at)
    VALUES (:rideId, :userId, :amount, 'CASH', 'PENDING', '{}', NOW())
    """, nativeQuery = true)
    void createPendingPayment(@Param("rideId") Long rideId,
                              @Param("userId") Long userId,
                              @Param("amount") Double amount);
```

**REPLACE WITH (New code):**
```java
    // ── S3-F4 ──────────────────────────────────────────
    // REMOVED: setDriverAvailable — M3 publishes ride.completed event; driver-service consumes and updates
    // REMOVED: createPendingPayment — M3 publishes ride.completed event; payment-service consumes and creates
```

---

## SNIPPET 3: S3-F7 RideService.java

### Location: Import section (after line 20)
**Add after line 20: `import com.team21.uber.contracts.events.RidePlacedEvent;`**

```diff
  import com.team21.uber.contracts.events.RidePlacedEvent;
+ import com.team21.uber.contracts.events.RideCancelledEvent;
  import com.team21.uber.contracts.feign.DriverServiceClient;
```

### Location: cancelRide() method (line 274)
**REPLACE the entire cancelRide() method**

**REMOVE (Original code):**
```java
    // S3-F7
    @Transactional
    public void cancelRide(Long id) {
        Ride ride = rideRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));
        if (ride.getStatus() != RideStatus.REQUESTED && ride.getStatus() != RideStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only REQUESTED or ACCEPTED rides can be cancelled");
        }
        ride.setStatus(RideStatus.CANCELLED);
        if (ride.getDriverId() != null) {
            rideRepository.updateDriverStatus(ride.getDriverId(), "AVAILABLE");
        }
        rideRepository.save(ride);
    }
```

**REPLACE WITH (New code):**
```java
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
```

---

## SNIPPET 4: S3-F7 RideRepository.java

### Location: S3-F2 section (around line 35-44 in original file)

**REMOVE (Original code):**
```java
    // ── S3-F2 ──────────────────────────────────────────
    // REMOVED: findDriverStatusById  — M3 uses Feign → driver-service GET /api/drivers/{id}
    // KEPT   : updateDriverStatus    — still used by S3-F7 cancelRide until that is refactored

    // S3-F2 / S3-F7: update driver status (cross-service native SQL)
    // TODO(S3-F7): remove once cancelRide is refactored to publish ride.cancelled event
    @Modifying
    @Transactional
    @Query(value = "UPDATE drivers SET status = :status WHERE id = :driverId", nativeQuery = true)
    void updateDriverStatus(@Param("driverId") Long driverId,
                            @Param("status") String status);
```

**REPLACE WITH (New code):**
```java
    // ── S3-F2 ──────────────────────────────────────────
    // REMOVED: findDriverStatusById  — M3 uses Feign → driver-service GET /api/drivers/{id}
    // REMOVED: updateDriverStatus    — M3 publishes ride.cancelled event; driver-service consumes and updates
```

### Location: S3-F7 section (around line 85-88 in original file)

**REMOVE (Original code):**
```java
    // S3-F7
    @Modifying
    @Query(value = "UPDATE drivers SET status = 'AVAILABLE' WHERE id = :driverId", nativeQuery = true)
    void updateDriverStatusToAvailable(@Param("driverId") Long driverId);
```

**REPLACE WITH (New code):**
```java
    // S3-F7 — REMOVED: updateDriverStatusToAvailable
    // Driver re-availability happens asynchronously when driver-service consumes ride.cancelled
```

---

## SUMMARY OF CHANGES

### S3-F4 Branch (2 files):
1. **RideService.java**: 3 imports + 1 field + constructor parameter + constructor assignment + complete `completeRide()` method refactor
2. **RideRepository.java**: Remove `setDriverAvailable()` and `createPendingPayment()` methods

### S3-F7 Branch (2 files):
1. **RideService.java**: 1 import + complete `cancelRide()` method refactor
2. **RideRepository.java**: Remove `updateDriverStatus()` and `updateDriverStatusToAvailable()` methods

