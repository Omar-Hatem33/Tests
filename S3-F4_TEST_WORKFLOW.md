# S3-F4 Complete Ride - Test Workflow

## Prerequisites
1. All services running:
   - user-service (port 8080)
   - driver-service (port 8080)
   - ride-service (port 8080)
   - location-service (port 8080)
   - payment-service (port 8080)
2. RabbitMQ running and accessible
3. All databases initialized (PostgreSQL instances for each service)
4. Postman installed with collection imported

## Test Variables Setup
Before running tests, set these in Postman environment:
```
base_url = http://localhost:8080 (or api-gateway:8080 if using Spring Cloud Gateway)
jwt_token = <valid JWT token with appropriate roles>
```

## Execution Flow

### Phase 1: Setup Data (Setup requests in Postman collection)
Execute in order to establish baseline data:

#### 1.1 Create User (ACTIVE)
```
POST /api/users
Body: {
  "id": 10,
  "name": "Test User",
  "email": "testuser@example.com",
  "status": "ACTIVE",
  "phone": "+1234567890"
}
Expected: 201 Created, user stored in user-postgres
```

#### 1.2 Create Driver (BUSY)
```
POST /api/drivers
Body: {
  "id": 5,
  "name": "Test Driver",
  "email": "driver@example.com",
  "status": "BUSY",
  "licenseNumber": "DL123456",
  "vehicleDetails": {
    "make": "Toyota",
    "model": "Prius",
    "vehicleType": "ECO"
  }
}
Expected: 201 Created, driver stored in driver-postgres with status=BUSY
```

#### 1.3 Create Recent Location
```
POST /api/locations
Body: {
  "driverId": 5,
  "rideId": 1,
  "latitude": 40.7128,
  "longitude": -74.0060,
  "timestamp": "2024-05-16T10:00:00Z"  // Within last 5 minutes
}
Expected: 201 Created, location stored in location-postgres
```

#### 1.4 Create Ride (IN_PROGRESS)
```
POST /api/rides
Body: {
  "id": 1,
  "userId": 10,
  "driverId": 5,
  "pickupLatitude": 40.7128,
  "pickupLongitude": -74.0060,
  "dropoffLatitude": 40.7580,
  "dropoffLongitude": -73.9855,
  "status": "IN_PROGRESS",
  "requestedAt": "2024-05-16T09:00:00Z",
  "fare": null
}
Expected: 201 Created, ride stored in ride-postgres with status=IN_PROGRESS, fare=null
```

---

### Phase 2: Scenario A - Happy Path
**Goal:** Verify complete flow with all pre-checks passing

#### A1. Execute: Complete Ride Successfully
```
PUT /api/rides/1/complete
Headers: Authorization: Bearer {{jwt_token}}
Expected: 200 OK
Response: {
  "id": 1,
  "userId": 10,
  "driverId": 5,
  "status": "COMPLETED",
  "fare": <calculated_fare>,
  "completedAt": <timestamp>,
  ...
}

Verifications:
1. HTTP Status: 200 OK
2. Ride status: COMPLETED
3. Fare: Not null (calculated using surge pricing)
4. completedAt: Set to current timestamp
5. Check ride-service logs: "S3-F4: Published ride.completed event for ride 1"
```

#### A2. Verify: Ride Status is COMPLETED
```
GET /api/rides/1
Expected: 200 OK with status=COMPLETED
```

#### A3. Verify: No Direct Driver Update
```
Goal: Confirm ride-service did NOT directly update driver status

Check driver-postgres directly:
SELECT status FROM drivers WHERE id = 5;
Expected: BUSY (unchanged)
Reason: Driver update happens asynchronously when driver-service consumes ride.completed
```

#### A4. Verify: No Direct Payment Creation
```
Goal: Confirm ride-service did NOT directly create payment

Check payment-postgres directly:
SELECT * FROM payments WHERE ride_id = 1;
Expected: Empty or eventually populated after payment-service consumes ride.completed
```

#### A5. Verify: RabbitMQ Event Published
```
Goal: Confirm ride.completed was published to ride.events exchange

Method 1: Check ride-service logs for:
"S3-F4: Published ride.completed event for ride 1"

Method 2: Use RabbitMQ Management UI:
- Navigate to exchange "ride.events"
- Check routing key "ride.completed"
- Verify message payload: {rideId: 1, userId: 10, driverId: 5, fare: <amount>}

Method 3: Monitor ride-service consumer:
After 1-2 seconds, check ride-postgres:
SELECT status FROM rides WHERE id = 1;
Expected: PAYMENT_PENDING (after ride-service consumes payment.initiated)
```

#### A6. Async Event Processing (Optional)
```
Goal: Observe full saga chain

Wait 2-3 seconds for events to propagate:

1. payment-service consumes ride.completed
   - Creates PENDING payment in payment-postgres
   
2. ride-service consumes payment.initiated (from payment-service)
   - Updates ride status to PAYMENT_PENDING
   
3. User triggers payment: POST /api/payments/ride/1
   - Body: {"method": "CREDIT_CARD", "cardLastFour": "4242"}
   - Expected: 201 Created
   
4. payment-service publishes payment.completed
   
5. ride-service consumes payment.completed
   - Updates ride status to PAID
   
Verify final state:
GET /api/rides/1
Expected: status=PAID, with completedAt and fare set
```

---

### Phase 3: Scenario B - User DEACTIVATED (Pre-Check Failure)
**Goal:** Verify pre-check aborts event publication

#### B1. Update User to DEACTIVATED
```
PUT /api/users/10
Body: {"status": "DEACTIVATED"}
Expected: 200 OK
```

#### B2. Create New Ride (IN_PROGRESS)
```
Create a new ride (ID=3) with same setup as Phase 1
Status: IN_PROGRESS, driverId=5, userId=10
```

#### B3. Execute: Complete Ride (User DEACTIVATED)
```
PUT /api/rides/3/complete
Expected: 400 Bad Request
Response: {
  "error": "User must be ACTIVE to complete a ride. Current status: DEACTIVATED"
}

Verifications:
1. HTTP Status: 400
2. Error message: Contains "DEACTIVATED"
3. Ride status: Still IN_PROGRESS (unchanged)
4. No event published (check logs for absence of "ride.completed" message)
```

#### B4. Verify: No Event Published
```
Check ride-service logs: Should NOT see "S3-F4: Published ride.completed event"
Verify other services did NOT consume any event from this failed attempt
```

---

### Phase 4: Scenario C - Driver Not BUSY (Pre-Check Failure)
**Goal:** Verify driver status validation

#### C1. Reset User to ACTIVE
```
PUT /api/users/10
Body: {"status": "ACTIVE"}
Expected: 200 OK
```

#### C2. Update Driver to OFFLINE
```
PUT /api/drivers/5
Body: {"status": "OFFLINE"}
Expected: 200 OK
```

#### C3. Create New Ride (IN_PROGRESS)
```
Create new ride (ID=4) with driver 5 (currently OFFLINE)
```

#### C4. Execute: Complete Ride (Driver Not BUSY)
```
PUT /api/rides/4/complete
Expected: 400 Bad Request
Response: {
  "error": "Driver must be BUSY (actively assigned). Current status: OFFLINE"
}

Verifications:
1. HTTP Status: 400
2. Error includes actual driver status
3. Ride remains IN_PROGRESS
4. No event published
```

---

### Phase 5: Scenario D - No Recent Location (Pre-Check Failure)
**Goal:** Verify location ping freshness validation

#### D1. Reset Driver to BUSY
```
PUT /api/drivers/5
Body: {"status": "BUSY"}
Expected: 200 OK
```

#### D2. Update Location to Stale (>5 minutes old)
```
PUT /api/locations/5
Body: {
  "driverId": 5,
  "rideId": 4,
  "latitude": 40.7128,
  "longitude": -74.0060,
  "timestamp": "2024-05-16T09:00:00Z"  // 11+ minutes old
}
Expected: 200 OK
```

#### D3. Create New Ride (IN_PROGRESS)
```
Create new ride (ID=5) with driver 5 (stale location)
```

#### D4. Execute: Complete Ride (Stale Location)
```
PUT /api/rides/5/complete
Expected: 400 Bad Request
Response: {
  "error": "Driver not actively tracked (no recent location ping within 5 minutes)"
}

Verifications:
1. HTTP Status: 400
2. Error message clear about 5-minute requirement
3. Ride remains IN_PROGRESS
4. No event published
5. Check logs for location service call details
```

---

### Phase 6: Scenario E - Cancel Ride Before Assignment
**Goal:** Test S3-F7 cancel with null driverId

#### E1. Create REQUESTED Ride (No Driver)
```
POST /api/rides
Body: {
  "id": 6,
  "userId": 10,
  "driverId": null,
  "pickupLatitude": 40.7128,
  "pickupLongitude": -74.0060,
  "dropoffLatitude": 40.7580,
  "dropoffLongitude": -73.9855,
  "status": "REQUESTED",
  "requestedAt": "2024-05-16T12:00:00Z"
}
Expected: 201 Created
```

#### E2. Execute: Cancel Ride
```
PUT /api/rides/6/cancel
Expected: 200 OK (no response body)

Verifications:
1. HTTP Status: 200
2. Ride status: CANCELLED
3. Check logs: "S3-F7: Published ride.cancelled event for ride 6 with reason=user_requested"
```

#### E3. Verify: ride.cancelled Event Published
```
Check for event payload: {
  "rideId": 6,
  "userId": 10,
  "driverId": null,
  "reason": "user_requested"
}

Verify driver-service silently ignores null driverId
Verify no driver updates occur
```

---

### Phase 7: Error Cases
**Goal:** Test edge cases and error handling

#### E7.1: Complete Non-Existent Ride (404)
```
PUT /api/rides/999/complete
Expected: 404 Not Found
Response: {"error": "Ride not found"}
```

#### E7.2: Complete COMPLETED Ride (400)
```
PUT /api/rides/1/complete  (from Phase 2, already COMPLETED)
Expected: 400 Bad Request
Response: {
  "error": "Ride must be IN_PROGRESS to complete. Current status: COMPLETED"
}
```

#### E7.3: Cancel COMPLETED Ride (400)
```
PUT /api/rides/1/cancel  (from Phase 2, already COMPLETED)
Expected: 400 Bad Request
Response: {
  "error": "Only REQUESTED or ACCEPTED rides can be cancelled. Current status: COMPLETED"
}
```

#### E7.4: Cancel Non-Existent Ride (404)
```
PUT /api/rides/999/cancel
Expected: 404 Not Found
Response: {"error": "Ride not found"}
```

---

## Key Validation Checklist

- [ ] Pre-checks run BEFORE any database mutation
- [ ] Pre-check failures return 400 with clear messages
- [ ] Pre-check failures do NOT publish events
- [ ] ride.completed event published with correct payload
- [ ] ride.cancelled event published with reason="user_requested"
- [ ] Ride status set to COMPLETED after pre-checks pass
- [ ] Fare calculated using surge pricing (or kept if already set)
- [ ] completedAt timestamp set when ride completed
- [ ] No direct driver status updates from ride-service
- [ ] No direct payment creation from ride-service
- [ ] Logging includes S3-F4/S3-F7 prefixes
- [ ] Transaction commits before event publishes
- [ ] All Feign calls wrapped in try-catch
- [ ] Correlation IDs propagated across Feign calls

---

## Troubleshooting

### Issue: Pre-checks always fail with 503
**Cause:** Feign client can't reach target service
**Fix:** Verify all services running and URLs correct in feign config

### Issue: Events published but not consumed
**Cause:** Consumer queues not bound to exchange
**Fix:** Verify RabbitMQ queue/exchange/binding setup per M3 spec

### Issue: Ride status not updating after events
**Cause:** Event consumer not processing messages
**Fix:** Check consumer service logs for binding/processing errors

### Issue: Location check always fails
**Cause:** Location timestamp format mismatch
**Fix:** Use ISO-8601 format: "2024-05-16T10:00:00Z"

---

## Expected Timeline

| Phase | Duration | Notes |
|-------|----------|-------|
| Setup (Phase 1) | 30s | Create test data |
| Happy Path (Phase 2) | 1-2m | Includes async processing wait |
| Scenario B (Phase 3) | 30s | Sync failure test |
| Scenario C (Phase 4) | 30s | Sync failure test |
| Scenario D (Phase 5) | 30s | Sync failure test |
| Scenario E (Phase 6) | 30s | Cancel test |
| Error Cases (Phase 7) | 30s | Edge cases |
| **Total** | **~5 minutes** | Full regression suite |

---

## Log Patterns to Verify

### Successful Complete
```
[S3-F4 pre-check] Calling UserServiceClient.getUser with userId=10
[S3-F4 pre-check] User 10 is ACTIVE
[S3-F4 pre-check] Calling DriverServiceClient.getDriver with driverId=5
[S3-F4 pre-check] Driver 5 is BUSY
[S3-F4 pre-check] Calling LocationServiceClient.getRecentLocationForDriver with driverId=5
[S3-F4 pre-check] Driver 5 has recent location ping
S3-F4: Calculated fare for ride 1. Distance=6.5, surgeMultiplier=1.0, fare=97.5
S3-F4: Ride 1 marked COMPLETED with fare=97.5
S3-F4: Published ride.completed event for ride 1
```

### Failed Pre-Check (User DEACTIVATED)
```
[S3-F4 pre-check] Calling UserServiceClient.getUser with userId=10
[S3-F4 pre-check FAILED] User 10 status=DEACTIVATED
```

### Successful Cancel
```
S3-F7: Ride 6 marked CANCELLED
S3-F7: Published ride.cancelled event for ride 6 with reason=user_requested
```
