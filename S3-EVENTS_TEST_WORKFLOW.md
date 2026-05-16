# S3-EVENTS: Test Workflow for New Endpoints

## Prerequisites

Before testing, ensure:

1. **Database is running** with sample ride data
2. **ride-service is started** on port 8080
3. **Postman is installed** (or use curl for manual testing)
4. **Test data exists** in the `rides` table with various statuses (REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, PAID, CANCELLED, PAYMENT_PENDING)

---

## Manual Test Workflow (cURL)

### Step 1: Create Test Data (Optional)

If you don't have test rides, insert some:

```sql
-- Insert test user with multiple rides
INSERT INTO rides (user_id, driver_id, status, fare, requested_at, completed_at)
VALUES 
  (1, 10, 'COMPLETED', 25.50, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 29 DAY),
  (1, 11, 'COMPLETED', 18.75, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 19 DAY),
  (1, 12, 'PAID', 22.00, NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 14 DAY),
  (1, 13, 'CANCELLED', 0, NOW() - INTERVAL 10 DAY, NULL),
  (1, 14, 'IN_PROGRESS', 0, NOW() - INTERVAL 2 DAY, NULL),
  (1, 15, 'ACCEPTED', 0, NOW() - INTERVAL 1 HOUR, NULL),
  (2, 10, 'COMPLETED', 30.00, NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 24 DAY),
  (2, 11, 'COMPLETED', 35.50, NOW() - INTERVAL 18 DAY, NOW() - INTERVAL 17 DAY);

-- Insert test driver with multiple rides
INSERT INTO rides (user_id, driver_id, status, fare, requested_at, completed_at)
VALUES
  (10, 20, 'COMPLETED', 25.00, NOW() - INTERVAL 30 DAY, NOW() - INTERVAL 29 DAY),
  (11, 20, 'COMPLETED', 30.00, NOW() - INTERVAL 25 DAY, NOW() - INTERVAL 24 DAY),
  (12, 20, 'PAID', 28.50, NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 19 DAY),
  (13, 20, 'IN_PROGRESS', 0, NOW() - INTERVAL 2 DAY, NULL),
  (14, 20, 'PAYMENT_PENDING', 32.00, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);
```

### Step 2: Test User Endpoints

#### Test S1-F3: Get User Ride Summary

```bash
curl -X GET "http://localhost:8080/api/rides/user/1/summary" \
  -H "Content-Type: application/json"
```

**Expected Response**:
```json
{
  "totalRides": 7,
  "completedRides": 5,
  "cancelledRides": 1,
  "totalSpent": 166.25,
  "averageFare": 23.75
}
```

**Interpretation**:
- 7 total rides (all statuses)
- 5 completed (COMPLETED + PAID statuses)
- 1 cancelled
- $166.25 spent (sum of COMPLETED + PAID fares)
- $23.75 average fare

---

#### Test S1-F4: Get User Active Count

```bash
curl -X GET "http://localhost:8080/api/rides/user/1/active-count" \
  -H "Content-Type: application/json"
```

**Expected Response**:
```json
3
```

**Interpretation**:
- 3 rides in active states (REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, PAYMENT_PENDING)
- Does NOT include CANCELLED rides

---

#### Test S1-F9: Get User Completed Count

```bash
curl -X GET "http://localhost:8080/api/rides/user/1/completed-count" \
  -H "Content-Type: application/json"
```

**Expected Response**:
```json
5
```

**Interpretation**:
- 5 rides marked as complete (COMPLETED + PAID statuses only)

---

### Step 3: Test Driver Endpoints

#### Test S2-F3: Get Driver Ride Summary (No Date Range)

```bash
curl -X GET "http://localhost:8080/api/rides/driver/20/summary" \
  -H "Content-Type: application/json"
```

**Expected Response**:
```json
{
  "totalRides": 5,
  "totalEarnings": 113.50,
  "averageFare": 22.70
}
```

**Interpretation**:
- 5 total rides for driver 20
- $113.50 total earnings (sum of COMPLETED + PAID fares only)
- $22.70 average fare per completed ride

---

#### Test S2-F3: Get Driver Ride Summary (With Date Range)

```bash
curl -X GET "http://localhost:8080/api/rides/driver/20/summary?startDate=2026-01-01&endDate=2026-05-16" \
  -H "Content-Type: application/json"
```

**Expected Response**:
```json
{
  "totalRides": 4,
  "totalEarnings": 113.50,
  "averageFare": 28.38
}
```

**Interpretation**:
- Filtered by date range provided
- Only counts rides within specified dates
- Returns 0s if no rides in range

---

#### Test S2-F4: Get Driver Active Count

```bash
curl -X GET "http://localhost:8080/api/rides/driver/20/active-count" \
  -H "Content-Type: application/json"
```

**Expected Response**:
```json
2
```

**Interpretation**:
- 2 rides in active states (ACCEPTED, IN_PROGRESS, COMPLETED, PAYMENT_PENDING)

---

#### Test S2-F6: Get Driver Completed Count

```bash
curl -X GET "http://localhost:8080/api/rides/driver/20/completed-count" \
  -H "Content-Type: application/json"
```

**Expected Response**:
```json
4
```

**Interpretation**:
- 4 rides marked complete (COMPLETED + PAID)

---

## Postman Test Workflow

### 1. Import the Collection

1. Open Postman
2. Click **Import** → **Choose Files**
3. Select: `S3-EVENTS_POSTMAN_TESTS.json`
4. Click **Import**

### 2. Set Environment Variables (Optional)

If you want to use variables instead of hardcoded values:

1. Create a new Environment
2. Set variables:
   - `base_url` = `http://localhost:8080`
   - `user_id` = `1`
   - `driver_id` = `20`

3. Modify request URLs to use variables:
   - Replace `http://localhost:8080` with `{{base_url}}`
   - Replace `1` with `{{user_id}}`
   - Replace `20` with `{{driver_id}}`

### 3. Run All Tests

1. Select the collection in the left sidebar
2. Click the **▶ Run** button (Run Collection)
3. Choose your environment
4. Click **Run S3-EVENTS: New Read Endpoints**

### 4. Verify Results

All tests should return:
- ✅ Status 200 OK
- ✅ Response body with correct data types
- ✅ No errors in console

---

## Response Validation Tests

### Test 1: Non-existent User Summary

```bash
curl -X GET "http://localhost:8080/api/rides/user/99999/summary"
```

**Expected**: 200 OK with zeros
```json
{
  "totalRides": 0,
  "completedRides": 0,
  "cancelledRides": 0,
  "totalSpent": 0.0,
  "averageFare": 0.0
}
```

---

### Test 2: Non-existent Driver Summary

```bash
curl -X GET "http://localhost:8080/api/rides/driver/99999/summary"
```

**Expected**: 200 OK with zeros
```json
{
  "totalRides": 0,
  "totalEarnings": 0.0,
  "averageFare": 0.0
}
```

---

### Test 3: Non-existent User Active Count

```bash
curl -X GET "http://localhost:8080/api/rides/user/99999/active-count"
```

**Expected**: 200 OK
```json
0
```

---

## Status Code Reference

| Code | Meaning | When |
|------|---------|------|
| 200 | OK | Request successful, data returned |
| 400 | Bad Request | Invalid path parameter (not a number) |
| 404 | Not Found | Endpoint doesn't exist |
| 500 | Server Error | Database or service error |

---

## Debugging

### If you get 404 errors:

1. Verify ride-service is running: `http://localhost:8080/api/rides` (should list rides)
2. Check if new endpoints are reachable
3. Rebuild and restart if code changes weren't picked up

### If you get 500 errors:

1. Check ride-service logs for SQL errors
2. Verify database connectivity
3. Ensure `rides` table exists and has data

### If you get unexpected values:

1. Check your test data in the database:
   ```sql
   SELECT id, user_id, driver_id, status, fare FROM rides WHERE user_id = 1 LIMIT 10;
   ```
2. Verify ride statuses are uppercase (COMPLETED, PAID, etc.)
3. Check date ranges if using date filters

---

## Performance Testing

### Test with Large User IDs

```bash
for i in {1..100}; do
  curl -X GET "http://localhost:8080/api/rides/user/$i/summary" &
done
wait
```

All requests should complete in < 100ms due to database indexing on `user_id`.

---

## Status

✅ **READY FOR TESTING**

All 6 endpoints are implemented and ready for validation. Use the Postman collection or cURL commands above to verify functionality.
