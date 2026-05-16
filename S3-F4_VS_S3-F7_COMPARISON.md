# S3-F4 vs S3-F7: Side-by-Side Comparison

## Quick Summary

| Feature | S3-F4 | S3-F7 |
|---------|-------|-------|
| **Branch Name** | s3-f4-complete-ride | s3-f7-cancel-ride |
| **Commit Hash** | 7474bf2 | 65be2f2 |
| **Method** | completeRide(Long id) | cancelRide(Long id) |
| **Purpose** | Mark ride as COMPLETED and trigger payment saga | Mark ride as CANCELLED and release driver |
| **HTTP Method** | POST | DELETE |
| **Endpoint** | POST /api/rides/{id}/complete | DELETE /api/rides/{id} |
| **Pre-conditions** | Status = IN_PROGRESS | Status ∈ {REQUESTED, ACCEPTED} |
| **Event Published** | ride.completed | ride.cancelled |
| **Files Changed** | 2 (RideService, RideRepository) | 2 (RideService, RideRepository) |
| **Complexity** | HIGH (3 pre-checks, fare calc) | LOW (status validation) |

---

## S3-F4: Complete Ride (Saga Trigger)

### Branch Scope
- **Base**: Commit 81d3c77
- **Branch**: s3-f4-complete-ride
- **Commit**: 7474bf2

### Implementation Details

#### RideService.java Changes

**New Imports**:
```java
import com.team21.uber.contracts.feign.LocationServiceClient;
import com.team21.uber.contracts.dto.LocationDTO;
import com.team21.uber.contracts.events.RideCompletedEvent;
```

**New Dependencies**:
```java
private final LocationServiceClient locationServiceClient;
```

**Constructor Update**:
```java
// BEFORE: 10 parameters
public RideService(..., UserServiceClient userServiceClient) { ... }

// AFTER: 11 parameters
public RideService(..., UserServiceClient userServiceClient, 
                   LocationServiceClient locationServiceClient) { ... }
```

#### completeRide() Method (Detailed Breakdown)

**STEP 1: Find Ride**
```java
Ride ride = rideRepository.findById(id)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));
```
- Return: 404 if ride doesn't exist
- No database isolation violation

**STEP 2: Validate Status**
```java
if (ride.getStatus() != RideStatus.IN_PROGRESS) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Ride must be IN_PROGRESS to complete...");
}
```
- Return: 400 if status ≠ IN_PROGRESS
- Example invalid: COMPLETED, CANCELLED, REQUESTED, ACCEPTED

**STEP 3a: User ACTIVE Check (Feign)**
```java
UserDTO userDTO = userServiceClient.getUser(userId);
if (!"ACTIVE".equals(userDTO.status())) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "User must be ACTIVE to complete a ride...");
}
```
- Calls: user-service GET /api/users/{id}
- Return: 400 if user not ACTIVE
- Return: 400 if user not found (404 → caught)
- Return: 503 if Feign call fails

**STEP 3b: Driver BUSY Check (Feign)**
```java
DriverDTO driverDTO = driverServiceClient.getDriver(driverId);
if (!"BUSY".equals(driverDTO.status())) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Driver must be BUSY (actively assigned)...");
}
```
- Calls: driver-service GET /api/drivers/{id}
- Return: 400 if driver not BUSY
- Return: 400 if driver not found (404 → caught)
- Return: 503 if Feign call fails

**STEP 3c: Location Recent Ping Check (Feign)**
```java
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
```
- Calls: location-service GET /api/locations/driver/{id}/recent
- Return: 400 if location data missing
- Return: 400 if location older than 5 minutes
- Return: 400 if not found (404 → caught)
- Return: 503 if Feign call fails

**STEP 4: Calculate Fare**
```java
if (ride.getFare() == null) {
    // Get nearby active rides count
    int nearbyCount = rideRepository.countNearbyActiveRides(
            ride.getPickupLatitude() - 0.01, 
            ride.getPickupLatitude() + 0.01,
            ride.getPickupLongitude() - 0.01, 
            ride.getPickupLongitude() + 0.01);
    
    // Apply surge multiplier
    double surgeMultiplier;
    if (nearbyCount <= 10) surgeMultiplier = 1.0;
    else if (nearbyCount <= 20) surgeMultiplier = 1.5;
    else surgeMultiplier = 2.0;
    
    // Calculate fare
    double distance = Math.sqrt(
            Math.pow(ride.getDropoffLatitude() - ride.getPickupLatitude(), 2) +
            Math.pow(ride.getDropoffLongitude() - ride.getPickupLongitude(), 2)) * 111;
    double fare = 15.0 * distance * surgeMultiplier;
    ride.setFare(Math.round(fare * 100.0) / 100.0);
}
```
- Checks if fare already set (from user estimate)
- Counts nearby active rides (0.01° radius ≈ 1km)
- Surge multiplier tiers:
  - ≤10 rides: 1.0x
  - ≤20 rides: 1.5x
  - >20 rides: 2.0x
- Formula: fare = (15.0 * distance_km) * surge_multiplier
- Rounds to 2 decimal places (cents)

**STEP 5: Save to Database**
```java
ride.setStatus(RideStatus.COMPLETED);
ride.setCompletedAt(LocalDateTime.now());
Ride saved = rideRepository.save(ride);
```
- Sets status to COMPLETED
- Records completion timestamp
- Persists to database (implicit transaction commit by @Transactional)
- Note: No direct driver update, no payment insert

**STEP 6: Publish Event**
```java
// Transaction commits FIRST (implicit)
// Then event publishes (after return from repo.save())
rideEventPublisher.publishRideCompleted(
    new RideCompletedEvent(
        saved.getId(), 
        saved.getUserId(), 
        driverId, 
        saved.getFare()
    )
);
```
- Event: `ride.completed`
- Exchange: `ride.events`
- Payload: {rideId, userId, driverId, fare}
- Timing: After database commit (implicit), before response

**STEP 7: Return Response**
```java
return saved;  // 200 OK with updated ride entity
```

#### RideRepository.java Changes

**Removed Methods**:
```java
// ❌ REMOVED
void setDriverAvailable(@Param("driverId") Long driverId);

// ❌ REMOVED
void createPendingPayment(@Param("rideId") Long rideId,
                          @Param("userId") Long userId,
                          @Param("amount") Double amount);
```

**Retained Methods**:
- `findByRequestedAtBetweenOrderByRequestedAtDesc()` ✓
- `findByStatusAndRequestedAtBetweenOrderByRequestedAtDesc()` ✓
- `existsByUserIdAndStatusIn()` ✓
- `countNearbyActiveRides()` ✓
- `findByMetadataField()` ✓
- `getRideAnalytics()` ✓
- `findUserNameById()` ✓

### Database Changes
- **None**: No schema modifications required
- Removed two cross-service SQL queries
- Retained all data retrieval queries

### Error Codes

| Code | Scenario | Action |
|------|----------|--------|
| 404 | Ride not found | Return NOT_FOUND |
| 400 | Status ≠ IN_PROGRESS | Return BAD_REQUEST |
| 400 | User not ACTIVE | Return BAD_REQUEST (pre-check failed) |
| 400 | Driver not BUSY | Return BAD_REQUEST (pre-check failed) |
| 400 | Location stale/missing | Return BAD_REQUEST (pre-check failed) |
| 503 | Feign call fails | Return SERVICE_UNAVAILABLE |
| 200 | All checks pass, event published | Return ride entity |

### Saga Chain (Async)

```
POST /api/rides/{id}/complete (200 OK)
        ↓
[ride.completed] event published
        ↓
├─→ payment-service consumes
│   └─→ Creates pending payment
│       └─→ Publishes payment.completed event
│           └─→ ride-service consumes payment.completed
│               └─→ Updates ride status to PAID
│
└─→ driver-service consumes
    └─→ Sets driver status to AVAILABLE
        └─→ Records completion history
```

---

## S3-F7: Cancel Ride (Saga Trigger)

### Branch Scope
- **Base**: Commit 81d3c77
- **Branch**: s3-f7-cancel-ride
- **Commit**: 65be2f2

### Implementation Details

#### RideService.java Changes

**New Imports**:
```java
import com.team21.uber.contracts.events.RideCancelledEvent;
```

**No New Dependencies**: No new fields added (unlike S3-F4)

#### cancelRide() Method (Detailed Breakdown)

**STEP 1: Find Ride**
```java
Ride ride = rideRepository.findById(id)
    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ride not found"));
```
- Return: 404 if ride doesn't exist
- Simple lookup, no Feign calls

**STEP 2: Validate Status**
```java
if (ride.getStatus() != RideStatus.REQUESTED && ride.getStatus() != RideStatus.ACCEPTED) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Only REQUESTED or ACCEPTED rides can be cancelled...");
}
```
- Return: 400 if status ∉ {REQUESTED, ACCEPTED}
- Invalid examples: COMPLETED, CANCELLED (already), IN_PROGRESS (too late)

**STEP 3: Save Cancellation**
```java
ride.setStatus(RideStatus.CANCELLED);
Ride saved = rideRepository.save(ride);
```
- Sets status to CANCELLED
- Persists to database
- No timestamp tracking (unlike COMPLETED which has completedAt)

**STEP 4: Publish Event**
```java
rideEventPublisher.publishRideCancelled(
    new RideCancelledEvent(
        saved.getId(), 
        saved.getUserId(), 
        saved.getDriverId(),  // Can be null if no driver assigned
        "user_requested"      // Reason
    )
);
```
- Event: `ride.cancelled`
- Exchange: `ride.events`
- Payload: {rideId, userId, driverId, reason}
- Reason: "user_requested" (fixed for this method)
- Note: driverId can be null (ride not yet assigned)

**STEP 5: Return**
```java
// void method, returns 200 OK with no body
```

#### RideRepository.java Changes

**Removed Methods**:
```java
// ❌ REMOVED
@Modifying
@Transactional
@Query(value = "UPDATE drivers SET status = :status WHERE id = :driverId", nativeQuery = true)
void updateDriverStatus(@Param("driverId") Long driverId, @Param("status") String status);

// ❌ REMOVED
@Modifying
@Query(value = "UPDATE drivers SET status = 'AVAILABLE' WHERE id = :driverId", nativeQuery = true)
void updateDriverStatusToAvailable(@Param("driverId") Long driverId);
```

**Retained Methods**: All same as S3-F4 (unchanged from base)

### Database Changes
- **None**: No schema modifications required
- Removed two direct driver status update methods
- No impact on data retrieval

### Error Codes

| Code | Scenario | Action |
|------|----------|--------|
| 404 | Ride not found | Return NOT_FOUND |
| 400 | Status ∉ {REQUESTED, ACCEPTED} | Return BAD_REQUEST |
| 200 | Cancellation successful, event published | Return 200 OK (void) |

### Saga Chain (Async)

```
DELETE /api/rides/{id} (200 OK)
        ↓
[ride.cancelled] event published
        ↓
└─→ driver-service consumes (if driverId != null)
    └─→ Sets driver status to AVAILABLE
        └─→ Records cancellation history
```

---

## Key Differences

### Complexity
- **S3-F4**: Complex (5 checks, 2 Feign calls, fare calculation)
- **S3-F7**: Simple (1 check, no Feign calls)

### Pre-conditions
- **S3-F4**: Requires all 3 Feign checks to pass before any mutation
- **S3-F7**: Only requires status validation

### Feign Usage
- **S3-F4**: 3 Feign calls (user-service, driver-service, location-service)
- **S3-F7**: 0 Feign calls

### Fare Handling
- **S3-F4**: Calculates fare with surge pricing algorithm
- **S3-F7**: No fare calculation

### Driver Re-availability
- **S3-F4**: Driver set AVAILABLE asynchronously (via saga)
- **S3-F7**: Driver set AVAILABLE asynchronously (via saga)

### Event Content
- **S3-F4**: ride.completed {rideId, userId, driverId, fare}
- **S3-F7**: ride.cancelled {rideId, userId, driverId, reason}

### Return Type
- **S3-F4**: Returns updated Ride entity (200 OK with body)
- **S3-F7**: Returns void (200 OK with no body)

---

## Testing Comparison

### S3-F4 Test Cases (7 total)

1. **Happy Path (Scenario A)**: All pre-checks pass
   - Expected: 200, ride.completed event published
   
2. **User Not ACTIVE (Scenario B)**: Pre-check fails
   - Expected: 400, no event published
   
3. **Driver Not BUSY (Scenario C)**: Pre-check fails
   - Expected: 400, no event published
   
4. **No Recent Location (Scenario D)**: Pre-check fails
   - Expected: 400, no event published
   
5. **Ride Not Found**: Error case
   - Expected: 404
   
6. **Invalid Status**: Error case
   - Expected: 400
   
7. **Service Unavailable**: Error case (Feign fails)
   - Expected: 503

### S3-F7 Test Cases (5 total)

1. **Cancel REQUESTED (Scenario E)**: Happy path
   - Expected: 200, ride.cancelled event published
   
2. **Cancel ACCEPTED (Scenario F)**: Happy path
   - Expected: 200, ride.cancelled event published
   
3. **Ride Not Found**: Error case
   - Expected: 404
   
4. **Cannot Cancel IN_PROGRESS**: Error case
   - Expected: 400
   
5. **Cannot Cancel Already CANCELLED**: Error case
   - Expected: 400

---

## Deployment Order

### Recommended Sequence
1. Deploy **S3-F7** first (simpler, fewer dependencies)
2. Deploy **S3-F4** second (depends on all microservices up)

### Dependencies

**S3-F4 Requires**:
- ✓ user-service with GET /api/users/{id}
- ✓ driver-service with GET /api/drivers/{id}
- ✓ location-service with GET /api/locations/driver/{id}/recent
- ✓ payment-service listening for ride.completed
- ✓ driver-service listening for ride.completed

**S3-F7 Requires**:
- ✓ driver-service listening for ride.cancelled

---

## Summary

Both refactors successfully eliminate database isolation violations by:
1. Removing direct cross-service SQL queries
2. Implementing event-driven saga patterns
3. Moving async compensation to dedicated consumers

**S3-F4** is a comprehensive refactor with validation and fare calculation, while **S3-F7** is a simpler event publisher for cancellations. Both are independent, testable, and deployable on separate branches.
