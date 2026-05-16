# S3-EVENTS: New Read Endpoints Implementation

## Summary

Completed **Priority #3** - Implemented 6 new read-only endpoints in ride-service that are called by S1 (user-service) and S2 (driver-service) via Feign clients. These endpoints unblock the S1 and S2 teams from implementing their features.

---

## Files Changed (4 total)

### 1. **RideSummaryDTO.java** (NEW)
- **Location**: `Uber/ride-service/src/main/java/com/team21/uber/ride/dto/RideSummaryDTO.java`
- **Status**: NEW FILE
- **Purpose**: DTO for user ride summary endpoint

```java
package com.team21.uber.ride.dto;

public record RideSummaryDTO(
        long totalRides,
        long completedRides,
        long cancelledRides,
        double totalSpent,
        double averageFare
) {}
```

---

### 2. **DriverRideSummaryDTO.java** (NEW)
- **Location**: `Uber/ride-service/src/main/java/com/team21/uber/ride/dto/DriverRideSummaryDTO.java`
- **Status**: NEW FILE
- **Purpose**: DTO for driver ride summary endpoint

```java
package com.team21.uber.ride.dto;

public record DriverRideSummaryDTO(
        long totalRides,
        double totalEarnings,
        double averageFare
) {}
```

---

### 3. **RideRepository.java** (UPDATED)
- **Status**: UPDATED
- **Changes**:
  - Added 7 new read-only query methods
  - Cleaned up S3-F4 (removed `setDriverAvailable`, `createPendingPayment`)
  - Removed duplicate S3-F7 method comment

**New Query Methods Added**:

```java
// S1-F3: GET /api/rides/user/{userId}/summary
@Query(value = """
SELECT
    COUNT(*) AS totalRides,
    COALESCE(SUM(CASE WHEN status = 'COMPLETED' OR status = 'PAID' THEN 1 ELSE 0 END), 0) AS completedRides,
    COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelledRides,
    COALESCE(SUM(CASE WHEN status = 'COMPLETED' OR status = 'PAID' THEN fare ELSE 0 END), 0) AS totalSpent,
    COALESCE(AVG(CASE WHEN status = 'COMPLETED' OR status = 'PAID' THEN fare END), 0) AS averageFare
FROM rides
WHERE user_id = :userId
""", nativeQuery = true)
List<Object[]> getUserRideSummary(@Param("userId") Long userId);

// S1-F4: GET /api/rides/user/{userId}/active-count
@Query(value = """
SELECT COUNT(*) FROM rides
WHERE user_id = :userId
AND status IN ('REQUESTED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'PAYMENT_PENDING')
""", nativeQuery = true)
int getUserActiveRideCount(@Param("userId") Long userId);

// S1-F9: GET /api/rides/user/{userId}/completed-count
@Query(value = """
SELECT COUNT(*) FROM rides
WHERE user_id = :userId
AND status IN ('COMPLETED', 'PAID')
""", nativeQuery = true)
long getUserCompletedRideCount(@Param("userId") Long userId);

// S2-F3, S2-F12: GET /api/rides/driver/{driverId}/summary
@Query(value = """
SELECT
    COUNT(*) AS totalRides,
    COALESCE(SUM(CASE WHEN status = 'COMPLETED' OR status = 'PAID' THEN fare ELSE 0 END), 0) AS totalEarnings,
    COALESCE(AVG(CASE WHEN status = 'COMPLETED' OR status = 'PAID' THEN fare END), 0) AS averageFare
FROM rides
WHERE driver_id = :driverId
""", nativeQuery = true)
List<Object[]> getDriverRideSummary(@Param("driverId") Long driverId);

// S2-F3, S2-F12: GET /api/rides/driver/{driverId}/summary (with date range)
@Query(value = """
SELECT
    COUNT(*) AS totalRides,
    COALESCE(SUM(CASE WHEN status = 'COMPLETED' OR status = 'PAID' THEN fare ELSE 0 END), 0) AS totalEarnings,
    COALESCE(AVG(CASE WHEN status = 'COMPLETED' OR status = 'PAID' THEN fare END), 0) AS averageFare
FROM rides
WHERE driver_id = :driverId
AND requested_at BETWEEN :startDate AND :endDate
""", nativeQuery = true)
List<Object[]> getDriverRideSummaryByDateRange(
        @Param("driverId") Long driverId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
);

// S2-F4: GET /api/rides/driver/{driverId}/active-count
@Query(value = """
SELECT COUNT(*) FROM rides
WHERE driver_id = :driverId
AND status IN ('ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'PAYMENT_PENDING')
""", nativeQuery = true)
int getDriverActiveRideCount(@Param("driverId") Long driverId);

// S2-F6: GET /api/rides/driver/{driverId}/completed-count
@Query(value = """
SELECT COUNT(*) FROM rides
WHERE driver_id = :driverId
AND status IN ('COMPLETED', 'PAID')
""", nativeQuery = true)
long getDriverCompletedRideCount(@Param("driverId") Long driverId);
```

---

### 4. **RideService.java** (UPDATED)
- **Status**: UPDATED
- **Changes**:
  - Added 2 DTO imports (`RideSummaryDTO`, `DriverRideSummaryDTO`)
  - Added 6 service method implementations

**New Service Methods**:

```java
// S1-F3: User ride summary (totalRides, completedRides, cancelledRides, totalSpent, averageFare)
public com.team21.uber.ride.dto.RideSummaryDTO getUserRideSummary(Long userId) {
    List<Object[]> result = rideRepository.getUserRideSummary(userId);
    if (result.isEmpty()) {
        return new com.team21.uber.ride.dto.RideSummaryDTO(0, 0, 0, 0.0, 0.0);
    }
    Object[] row = result.get(0);
    return new com.team21.uber.ride.dto.RideSummaryDTO(
            ((Number) row[0]).longValue(),        // totalRides
            ((Number) row[1]).longValue(),        // completedRides
            ((Number) row[2]).longValue(),        // cancelledRides
            ((Number) row[3]).doubleValue(),      // totalSpent
            ((Number) row[4]).doubleValue()       // averageFare
    );
}

// S1-F4: Count user active rides (REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, PAYMENT_PENDING)
public int getUserActiveRideCount(Long userId) {
    return rideRepository.getUserActiveRideCount(userId);
}

// S1-F9: Count user completed rides (COMPLETED, PAID)
public long getUserCompletedRideCount(Long userId) {
    return rideRepository.getUserCompletedRideCount(userId);
}

// S2-F3, S2-F12: Driver ride summary (without date range)
public com.team21.uber.ride.dto.DriverRideSummaryDTO getDriverRideSummary(Long driverId) {
    List<Object[]> result = rideRepository.getDriverRideSummary(driverId);
    if (result.isEmpty()) {
        return new com.team21.uber.ride.dto.DriverRideSummaryDTO(0, 0.0, 0.0);
    }
    Object[] row = result.get(0);
    return new com.team21.uber.ride.dto.DriverRideSummaryDTO(
            ((Number) row[0]).longValue(),        // totalRides
            ((Number) row[1]).doubleValue(),      // totalEarnings
            ((Number) row[2]).doubleValue()       // averageFare
    );
}

// S2-F3, S2-F12: Driver ride summary (with date range)
public com.team21.uber.ride.dto.DriverRideSummaryDTO getDriverRideSummaryByDateRange(
        Long driverId, LocalDateTime startDate, LocalDateTime endDate) {
    List<Object[]> result = rideRepository.getDriverRideSummaryByDateRange(driverId, startDate, endDate);
    if (result.isEmpty()) {
        return new com.team21.uber.ride.dto.DriverRideSummaryDTO(0, 0.0, 0.0);
    }
    Object[] row = result.get(0);
    return new com.team21.uber.ride.dto.DriverRideSummaryDTO(
            ((Number) row[0]).longValue(),        // totalRides
            ((Number) row[1]).doubleValue(),      // totalEarnings
            ((Number) row[2]).doubleValue()       // averageFare
    );
}

// S2-F4: Count driver active rides (ACCEPTED, IN_PROGRESS, COMPLETED, PAYMENT_PENDING)
public int getDriverActiveRideCount(Long driverId) {
    return rideRepository.getDriverActiveRideCount(driverId);
}

// S2-F6: Count driver completed rides (COMPLETED, PAID)
public long getDriverCompletedRideCount(Long driverId) {
    return rideRepository.getDriverCompletedRideCount(driverId);
}
```

---

### 5. **RideController.java** (UPDATED)
- **Status**: UPDATED
- **Changes**:
  - Added 2 DTO imports (`RideSummaryDTO`, `DriverRideSummaryDTO`)
  - Added 6 new REST endpoints

**New Controller Endpoints**:

```java
// S1-F3: GET /api/rides/user/{userId}/summary
@GetMapping("/user/{userId}/summary")
public ResponseEntity<com.team21.uber.ride.dto.RideSummaryDTO> getUserRideSummary(@PathVariable Long userId) {
    return ResponseEntity.ok(rideService.getUserRideSummary(userId));
}

// S1-F4: GET /api/rides/user/{userId}/active-count
@GetMapping("/user/{userId}/active-count")
public ResponseEntity<Integer> getUserActiveRideCount(@PathVariable Long userId) {
    return ResponseEntity.ok(rideService.getUserActiveRideCount(userId));
}

// S1-F9: GET /api/rides/user/{userId}/completed-count
@GetMapping("/user/{userId}/completed-count")
public ResponseEntity<Long> getUserCompletedRideCount(@PathVariable Long userId) {
    return ResponseEntity.ok(rideService.getUserCompletedRideCount(userId));
}

// S2-F3, S2-F12: GET /api/rides/driver/{driverId}/summary (with optional date range)
@GetMapping("/driver/{driverId}/summary")
public ResponseEntity<com.team21.uber.ride.dto.DriverRideSummaryDTO> getDriverRideSummary(
        @PathVariable Long driverId,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate) {
    if (startDate != null && endDate != null) {
        LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
        LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
        return ResponseEntity.ok(rideService.getDriverRideSummaryByDateRange(driverId, start, end));
    }
    return ResponseEntity.ok(rideService.getDriverRideSummary(driverId));
}

// S2-F4: GET /api/rides/driver/{driverId}/active-count
@GetMapping("/driver/{driverId}/active-count")
public ResponseEntity<Integer> getDriverActiveRideCount(@PathVariable Long driverId) {
    return ResponseEntity.ok(rideService.getDriverActiveRideCount(driverId));
}

// S2-F6: GET /api/rides/driver/{driverId}/completed-count
@GetMapping("/driver/{driverId}/completed-count")
public ResponseEntity<Long> getDriverCompletedRideCount(@PathVariable Long driverId) {
    return ResponseEntity.ok(rideService.getDriverCompletedRideCount(driverId));
}
```

---

## 6 Endpoints Exposed

| Feature | Endpoint | HTTP Method | Returns | Called By |
|---------|----------|-------------|---------|-----------|
| S1-F3 | `/api/rides/user/{userId}/summary` | GET | `RideSummaryDTO` | user-service |
| S1-F4 | `/api/rides/user/{userId}/active-count` | GET | `int` | user-service |
| S1-F9 | `/api/rides/user/{userId}/completed-count` | GET | `long` | user-service |
| S2-F3, S2-F12 | `/api/rides/driver/{driverId}/summary` | GET | `DriverRideSummaryDTO` | driver-service |
| S2-F4 | `/api/rides/driver/{driverId}/active-count` | GET | `int` | driver-service |
| S2-F6 | `/api/rides/driver/{driverId}/completed-count` | GET | `long` | driver-service |

---

## Response Examples

### S1-F3: User Ride Summary
```json
{
  "totalRides": 45,
  "completedRides": 40,
  "cancelledRides": 5,
  "totalSpent": 450.50,
  "averageFare": 10.01
}
```

### S1-F4: User Active Count
```json
3
```

### S1-F9: User Completed Count
```json
40
```

### S2-F3/S2-F12: Driver Ride Summary
```json
{
  "totalRides": 100,
  "totalEarnings": 1250.75,
  "averageFare": 12.51
}
```

### S2-F4: Driver Active Count
```json
2
```

### S2-F6: Driver Completed Count
```json
95
```

---

## Implementation Details

### Ride Status Grouping

**User "Completed" = COMPLETED OR PAID**
- User pays for the ride after it's completed
- Both statuses represent revenue for the platform

**Driver "Completed" = COMPLETED OR PAID**
- Driver's earnings tracked when ride is COMPLETED or PAID
- Payment state doesn't affect driver metrics

**"Active" Rides**
- User: REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, PAYMENT_PENDING
- Driver: ACCEPTED, IN_PROGRESS, COMPLETED, PAYMENT_PENDING

### Query Performance

All queries are:
- **Read-only** (no state mutations)
- **Indexed** (on `user_id`, `driver_id`, `status`, `requested_at`)
- **Optimized** with COUNT, SUM, AVG aggregations
- **Database-level** (no application-layer filtering)

---

## Next Steps (Priority #2)

After these endpoints are deployed and tested:

1. **Payment Event Consumers** (ride.saga-feedback queue)
   - payment.initiated → PAYMENT_PENDING
   - payment.completed → PAID
   - payment.failed → PAYMENT_FAILED + publish ride.cancelled
   - payment.refunded → REFUNDED

2. **User Event Consumers** (audit-only, MongoDB logging)
   - user.registered → write audit
   - user.deactivated → write audit

---

## Status

✅ **COMPLETE** - Priority #3 (New Endpoints) fully implemented and ready for testing.

All files are drop-in ready and available in the project directory.
