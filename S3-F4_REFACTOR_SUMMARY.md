# S3-F4 Complete Ride (Saga Trigger) — M3 Refactor Implementation

## Overview
This document details the refactoring of S3-F4 (Complete Ride) to implement the saga pattern for microservices. The ride-service no longer directly updates drivers or creates payments; instead, it publishes events that other services consume asynchronously.

## Files Modified

### 1. **RideService.java** (UPDATED)
**Location:** `ride-service/src/main/java/com/team21/uber/ride/service/RideService.java`

**Changes:**
- Added `LocationServiceClient` dependency injection
- Added `RideCompletedEvent` and `RideCancelledEvent` imports
- **Refactored `completeRide(Long id)` method:**
  - **Removed:** Direct driver status update and payment insertion via native SQL
  - **Added:** Three Feign pre-checks before publishing event:
    1. **User Service Check:** User must exist and status = ACTIVE
    2. **Driver Service Check:** Driver must exist and status = BUSY
    3. **Location Service Check:** Driver must have recent GPS ping (within 5 minutes)
  - **Fare Calculation:** Uses M1 surge-pricing formula locally (no cross-service call)
  - **Event Publishing:** Publishes `ride.completed` event with payload `{rideId, userId, driverId, fare}`
  - **Transaction Semantics:** Transaction commits first, then event publishes (commit-after-publish)

- **Refactored `cancelRide(Long id)` method:**
  - **Removed:** Direct driver status update to AVAILABLE
  - **Added:** Event publishing for `ride.cancelled` with reason "user_requested"
  - **Async Compensation:** Driver re-availability handled by driver-service event consumer

### 2. **RideRepository.java** (UPDATED)
**Location:** `ride-service/src/main/java/com/team21/uber/ride/repository/RideRepository.java`

**Removed Methods:**
- `setDriverAvailable(Long driverId)` — No longer needed; driver-service consumes ride.completed
- `createPendingPayment(Long rideId, Long userId, Double amount)` — No longer needed; payment-service consumes ride.completed
- `updateDriverStatus(Long driverId, String status)` — No longer needed; driver-service consumes ride.cancelled
- `updateDriverStatusToAvailable(Long driverId)` — No longer needed; driver-service consumes ride.cancelled

**Kept Methods:**
- All query methods for ride searches and analytics
- `countNearbyActiveRides(...)` — Used for surge pricing calculation in completeRide
- `findUserNameById(Long userId)` — Still used by S3-F12

## Key Implementation Details

### Pre-Saga Feign Checks (completeRide)
```java
1. UserServiceClient.getUser(userId)
   - Expected status: "ACTIVE"
   - Failure: 400 Bad Request
   - 404: 400 Bad Request ("User not found")
   - Other Feign errors: 503 Service Unavailable

2. DriverServiceClient.getDriver(driverId)
   - Expected status: "BUSY"
   - Failure: 400 Bad Request
   - 404: 400 Bad Request ("Driver not found")
   - Other Feign errors: 503 Service Unavailable

3. LocationServiceClient.getRecentLocationForDriver(driverId)
   - Expected: Location timestamp within last 5 minutes
   - Failure: 400 Bad Request ("Driver not actively tracked")
   - 404: 400 Bad Request ("No recent location ping")
   - Other Feign errors: 503 Service Unavailable
```

### Event Publishing
- **Exchange:** `ride.events` (TopicExchange)
- **Routing Keys:** 
  - `ride.completed` (from completeRide)
  - `ride.cancelled` (from cancelRide)
- **Event Payload Records:**
  - `RideCompletedEvent(Long rideId, Long userId, Long driverId, Double fare)`
  - `RideCancelledEvent(Long rideId, Long userId, Long driverId, String reason)`

### Idempotency Guarantees
- Feign pre-checks abort before any database mutation or event publication
- If a pre-check fails, no event is published and ride status remains unchanged
- This prevents partial saga execution with missing prerequisites

## Database Impact
- No direct updates to `drivers` table from ride-service
- No direct inserts into `payments` table from ride-service
- Only ride-service's own `rides` table is modified

## RabbitMQ Consumers
The ride.completed and ride.cancelled events flow through the saga:
- **ride.completed:** Consumed by driver-service, location-service, payment-service, user-service
- **ride.cancelled:** Consumed by driver-service, location-service, payment-service, user-service

## Testing Scenarios

### Scenario A: Happy Path
**Setup:**
- User ID=10 (status=ACTIVE) in user-postgres
- Driver ID=5 (status=BUSY, assigned) in driver-postgres
- Ride ID=1 (status=IN_PROGRESS, userId=10, driverId=5, fare=null) in ride-postgres
- Location for driver 5 with timestamp 2 minutes ago

**Action:** `PUT /api/rides/1/complete`

**Expected:**
- 200 OK
- Ride status → COMPLETED
- Ride fare calculated and set
- ride.completed published to ride.events

**Event Processing (Async):**
- payment-service consumes ride.completed → creates PENDING payment
- ride-service consumes payment.initiated → sets ride status PAYMENT_PENDING
- payment-service processes payment (user posts to /api/payments/ride/1)
- payment-service publishes payment.completed
- ride-service consumes payment.completed → sets ride status PAID
- driver-service consumes ride.completed → sets driver status AVAILABLE

### Scenario B: Pre-Check Failure - User DEACTIVATED
**Action:** PUT /api/rides/1/complete with user status=DEACTIVATED

**Expected:**
- 400 Bad Request
- Ride status remains IN_PROGRESS
- No event published

### Scenario C: Pre-Check Failure - Driver Not BUSY
**Action:** PUT /api/rides/1/complete with driver status=OFFLINE

**Expected:**
- 400 Bad Request
- Ride status remains IN_PROGRESS
- No event published

### Scenario D: Pre-Check Failure - No Recent Location
**Action:** PUT /api/rides/1/complete with last location > 5 minutes old

**Expected:**
- 400 Bad Request ("Driver not actively tracked")
- Ride status remains IN_PROGRESS
- No event published

### Scenario E: Cancel REQUESTED Ride
**Setup:**
- Ride ID=2 (status=REQUESTED, driverId=null) in ride-postgres

**Action:** PUT /api/rides/2/cancel

**Expected:**
- 200 OK
- Ride status → CANCELLED
- ride.cancelled published with driverId=null

**Event Processing:**
- driver-service consumes (silently ignores null driverId)
- No driver update occurs

## Logging
All S3-F4 operations are logged with prefix "S3-F4":
- Pre-check start/pass/fail events
- Fare calculation details
- Ride completion and event publication

All S3-F7 operations are logged with prefix "S3-F7":
- Ride cancellation
- Event publication

## Migration Notes
This refactor removes tight coupling between ride-service and driver/payment services. Cross-database operations are replaced with:
1. **Synchronous reads:** Feign calls for pre-saga validation
2. **Asynchronous writes:** RabbitMQ event publishing for state changes

This enables true service isolation and independent deployment of microservices.

## Files Affected Summary
| File | Type | Changes |
|------|------|---------|
| RideService.java | Updated | completeRide & cancelRide refactored; Added LocationServiceClient |
| RideRepository.java | Updated | Removed 4 methods; all cross-db operations eliminated |
| RideEventPublisher.java | No Change | Already implements publishRideCompleted & publishRideCancelled |
| RideController.java | No Change | Endpoints unchanged; logic moved to service |
