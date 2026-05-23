# VPS Test Guide — Live Deploy Validation

Public deploy URL: `http://213.136.67.218:30080`
Admin login: `admin@uber.local / adminpass`

All commands below are **PowerShell** (Windows team). Bash/curl alternatives at the bottom.

If a command returns the expected status code, that feature works on the live deploy. If it doesn't, paste the response back and we triage.

---

## 0. Sanity — is the cluster up?

```powershell
Invoke-WebRequest -Uri http://213.136.67.218:30080/actuator/health -UseBasicParsing | Select-Object StatusCode
# Expected: StatusCode = 200
```

If this fails, the VPS is down. Stop here and ping me.

---

## 1. Auth flow

### 1a. Login as admin (get JWT)

```powershell
$admin = Invoke-RestMethod -Method Post -Uri http://213.136.67.218:30080/api/auth/login `
  -ContentType 'application/json' `
  -Body '{"email":"admin@uber.local","password":"adminpass"}'
$ATOKEN = $admin.token
$ATOKEN.Substring(0, 30) + "..."
# Expected: prints JWT prefix
```

### 1b. Register a fresh rider

```powershell
$rnd = Get-Random
$reg = Invoke-RestMethod -Method Post -Uri http://213.136.67.218:30080/api/auth/register `
  -ContentType 'application/json' `
  -Body "{`"name`":`"Rider$rnd`",`"email`":`"r$rnd@x.com`",`"password`":`"pass123`",`"phone`":`"r$rnd`"}"
$RTOKEN = $reg.token
$RTOKEN.Substring(0, 30) + "..."
# Expected: JWT printed. Role=RIDER in token claim
```

### 1c. Duplicate register (409 expected)

```powershell
try {
  Invoke-RestMethod -Method Post -Uri http://213.136.67.218:30080/api/auth/register `
    -ContentType 'application/json' `
    -Body '{"name":"Admin","email":"admin@uber.local","password":"x","phone":"y"}'
} catch { $_.Exception.Response.StatusCode }
# Expected: Conflict (409)
```

---

## 2. Saga happy path (REQ -> PAID)

Run the whole sequence end to end. Decimal pad numbers vary, copy the random one each step.

```powershell
$rnd = Get-Random

# Rider
$rider = Invoke-RestMethod -Method Post -Uri http://213.136.67.218:30080/api/auth/register `
  -ContentType 'application/json' `
  -Body "{`"name`":`"R`",`"email`":`"saga-r-$rnd@x.com`",`"password`":`"p`",`"phone`":`"sr$rnd`"}"
$rtok = $rider.token
$USERID = [int]([System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String(($rtok.Split('.')[1] + '==').Substring(0, ($rtok.Split('.')[1].Length + 3) -band -bnot 3))) | ConvertFrom-Json).uid

# Driver-user
$dr = Invoke-RestMethod -Method Post -Uri http://213.136.67.218:30080/api/auth/register `
  -ContentType 'application/json' `
  -Body "{`"name`":`"D`",`"email`":`"saga-d-$rnd@x.com`",`"password`":`"p`",`"phone`":`"sd$rnd`"}"
$dtok = $dr.token

# Driver entity
$drv = Invoke-RestMethod -Method Post -Uri http://213.136.67.218:30080/api/drivers `
  -ContentType 'application/json' -Headers @{Authorization="Bearer $dtok"} `
  -Body "{`"name`":`"D`",`"email`":`"saga-d2-$rnd@x.com`",`"phone`":`"sx$rnd`",`"licenseNumber`":`"LIC-$rnd`",`"vehicleDetails`":{`"make`":`"T`",`"model`":`"C`",`"year`":2023,`"licensePlate`":`"X$rnd`",`"vehicleType`":`"SEDAN`"}}"
$DRVID = $drv.id

# Driver AVAILABLE (admin auth)
Invoke-RestMethod -Method Put -Uri "http://213.136.67.218:30080/api/drivers/$DRVID/availability" `
  -ContentType 'application/json' -Headers @{Authorization="Bearer $ATOKEN"} `
  -Body '{"status":"AVAILABLE"}'

# Driver location (recent, needed for saga pre-check)
Invoke-RestMethod -Method Post -Uri "http://213.136.67.218:30080/api/locations/driver/$DRVID" `
  -ContentType 'application/json' -Headers @{Authorization="Bearer $dtok"} `
  -Body '{"latitude":30.0,"longitude":31.0}'

# Request ride
$ride = Invoke-RestMethod -Method Post -Uri http://213.136.67.218:30080/api/rides `
  -ContentType 'application/json' -Headers @{Authorization="Bearer $rtok"} `
  -Body "{`"userId`":$USERID,`"pickupLatitude`":30.0,`"pickupLongitude`":31.0,`"dropoffLatitude`":30.1,`"dropoffLongitude`":31.1,`"fare`":100.0}"
$RIDEID = $ride.id
"RIDEID=$RIDEID DRVID=$DRVID USERID=$USERID"

# Assign
Invoke-RestMethod -Method Put -Uri "http://213.136.67.218:30080/api/rides/$RIDEID/assign?driverId=$DRVID" `
  -Headers @{Authorization="Bearer $dtok"}

# In progress
Invoke-RestMethod -Method Put -Uri "http://213.136.67.218:30080/api/rides/$RIDEID/status" `
  -ContentType 'application/json' -Headers @{Authorization="Bearer $dtok"} `
  -Body '{"status":"IN_PROGRESS"}'

# Complete (admin token — saga pre-checks call user-service)
Invoke-RestMethod -Method Put -Uri "http://213.136.67.218:30080/api/rides/$RIDEID/complete" `
  -Headers @{Authorization="Bearer $ATOKEN"}
Start-Sleep -Seconds 4

# Pay CASH
Invoke-RestMethod -Method Post -Uri "http://213.136.67.218:30080/api/payments/ride/$RIDEID" `
  -ContentType 'application/json' -Headers @{Authorization="Bearer $rtok"} `
  -Body '{"method":"CASH"}'
Start-Sleep -Seconds 3

# Final ride state
$final = Invoke-RestMethod -Uri "http://213.136.67.218:30080/api/rides/$RIDEID" `
  -Headers @{Authorization="Bearer $rtok"}
"FINAL STATUS = $($final.status)"
# Expected: FINAL STATUS = PAID
```

---

## 3. Saga compensation (FAILED -> REFUNDED)

Same flow as 2 up to complete, then pay with `BITCOIN` (unsupported):

```powershell
Invoke-RestMethod -Method Post -Uri "http://213.136.67.218:30080/api/payments/ride/$RIDEID" `
  -ContentType 'application/json' -Headers @{Authorization="Bearer $rtok"} `
  -Body '{"method":"BITCOIN"}' -SkipHttpErrorCheck
Start-Sleep -Seconds 8

$final = Invoke-RestMethod -Uri "http://213.136.67.218:30080/api/rides/$RIDEID" `
  -Headers @{Authorization="Bearer $rtok"}
"FINAL STATUS = $($final.status)"
# Expected: FINAL STATUS = REFUNDED
```

---

## 4. Auth/permissions tests

### 4a. Rider tries to view someone else's user payment summary -> 403

```powershell
try {
  Invoke-RestMethod -Uri "http://213.136.67.218:30080/api/payments/user/999/summary" `
    -Headers @{Authorization="Bearer $rtok"}
} catch { $_.Exception.Response.StatusCode }
# Expected: Forbidden (403)
```

### 4b. Rider hits ADMIN-only top-riders -> 403

```powershell
try {
  Invoke-RestMethod -Uri "http://213.136.67.218:30080/api/users/reports/top-riders" `
    -Headers @{Authorization="Bearer $rtok"}
} catch { $_.Exception.Response.StatusCode }
# Expected: Forbidden (403)
```

### 4c. Admin top-riders -> 200

```powershell
Invoke-RestMethod -Uri "http://213.136.67.218:30080/api/users/reports/top-riders?limit=5" `
  -Headers @{Authorization="Bearer $ATOKEN"}
# Expected: JSON array (may be empty if no rides)
```

### 4d. Gateway strips spoofed X-User-Role -> still 403 (no admin bypass)

```powershell
try {
  Invoke-RestMethod -Uri "http://213.136.67.218:30080/api/users/reports/top-riders" `
    -Headers @{Authorization="Bearer $rtok"; "X-User-Role"="ADMIN"}
} catch { $_.Exception.Response.StatusCode }
# Expected: Forbidden (403) — proves header spoofing blocked
```

---

## 5. Deactivate user (the bug we fixed today)

### 5a. Admin deactivates their own account -> 200

```powershell
# Re-login (last deactivate marked admin DEACTIVATED; we reactivated via SQL)
$ATOKEN = (Invoke-RestMethod -Method Post -Uri http://213.136.67.218:30080/api/auth/login `
  -ContentType 'application/json' `
  -Body '{"email":"admin@uber.local","password":"adminpass"}').token

Invoke-RestMethod -Method Put -Uri http://213.136.67.218:30080/api/users/11/deactivate `
  -Headers @{Authorization="Bearer $ATOKEN"}
# Expected: User object returned with status=DEACTIVATED
```

If you want to keep the admin usable for more tests, ping me to re-ACTIVATE via SQL.

---

## 6. Outbox proof (Bonus A)

You can't hit Postgres from outside the cluster directly, so this one needs SSH:

```bash
ssh root@213.136.67.218 "kubectl exec -n uber payment-postgres-0 -- psql -U postgres -d uberdb-payments -c 'SELECT id, routing_key, published_at FROM outbox ORDER BY id DESC LIMIT 10;'"
```

Expected: rows with `payment.initiated` / `payment.completed` / `payment.failed` / `payment.refunded`, all with `published_at` stamped (not null).

---

## 7. Driver flow

### 7a. List drivers (admin)

```powershell
Invoke-RestMethod -Uri http://213.136.67.218:30080/api/drivers `
  -Headers @{Authorization="Bearer $ATOKEN"}
```

### 7b. Driver dashboard

```powershell
Invoke-RestMethod -Uri "http://213.136.67.218:30080/api/drivers/$DRVID/dashboard" `
  -Headers @{Authorization="Bearer $ATOKEN"}
```

---

## 8. Search payments

```powershell
Invoke-RestMethod -Uri "http://213.136.67.218:30080/api/payments/search?status=COMPLETED" `
  -Headers @{Authorization="Bearer $ATOKEN"}
```

---

## 9. Coupon application

```powershell
# Apply coupon 1 to payment ID 2 (adjust IDs to ones that exist)
Invoke-RestMethod -Method Post -Uri "http://213.136.67.218:30080/api/payments/2/coupons/1" `
  -Headers @{Authorization="Bearer $ATOKEN"}
```

---

## 10. Bonus C — Redis lock contention (409 expected on race)

Run 10 parallel assign requests, only 1 wins:

```powershell
1..10 | ForEach-Object -Parallel {
  try {
    $r = Invoke-WebRequest -Method Put -Uri "http://213.136.67.218:30080/api/rides/$using:RIDEID/assign?driverId=$using:DRVID" `
      -Headers @{Authorization="Bearer $using:ATOKEN"} -UseBasicParsing -SkipHttpErrorCheck
    $r.StatusCode
  } catch { $_.Exception.Response.StatusCode.value__ }
} -ThrottleLimit 10
# Expected: one 200, rest 409 (Conflict) — lock contention proven
```

---

## Bash / curl equivalents

If you're on Linux/Mac/WSL, swap PowerShell for these. Just the login example:

```bash
ATOKEN=$(curl -s -X POST http://213.136.67.218:30080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@uber.local","password":"adminpass"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

curl -X PUT http://213.136.67.218:30080/api/users/11/deactivate \
  -H "Authorization: Bearer $ATOKEN"
```

---

## Quick scorecard

| # | Test | Expected | Why it matters |
|---|------|----------|----------------|
| 0 | Health check | 200 | Cluster alive |
| 1 | Register + login | JWT returned | S1 auth works |
| 2 | Full saga REQ -> PAID | status=PAID | Core M3 saga |
| 3 | BITCOIN -> REFUNDED | status=REFUNDED | Saga compensation |
| 4a-d | Auth checks | 403 on owner / role failures | H6-H9 enforced |
| 5 | Deactivate | 200 | Bug fix verified |
| 6 | Outbox stamped | rows with published_at | Bonus A |
| 10 | 10 parallel assign | 1x 200, 9x 409 | Bonus C Redis lock |

If any test above returns a different code, paste the response — we'll figure out what changed.

---

## 11. Monitoring stack (Loki + Prometheus + Grafana)

All three are live on VPS. Loki collects logs, Prometheus scrapes metrics, Grafana visualizes both.

### 11a. Grafana UI (public, in browser)

URL: **http://213.136.67.218:30030**
Login: `admin / admin` (Grafana prompts to change on first login)

- Dashboards → Browse — JVM heap dashboards per service
- Explore → Datasource: Prometheus — query metrics (`up`, `jvm_memory_used_bytes`, `http_server_requests_seconds_count`)
- Explore → Datasource: Loki — query logs (`{job="payment-service"}`, `{job=~".+-service"} |= "ERROR"`)

### 11b. Prometheus via gateway

```powershell
Invoke-RestMethod -Uri http://213.136.67.218:30080/actuator/prometheus -Headers @{Authorization="Bearer $ATOKEN"} | Select-Object -First 30
```

### 11c. Loki labels list

```bash
ssh root@213.136.67.218 "kubectl exec -n monitoring loki-0 -- wget -qO- 'http://localhost:3100/loki/api/v1/labels'"
```

### 11d. Observability proof

```powershell
1..5 | ForEach-Object { Invoke-WebRequest -Uri http://213.136.67.218:30080/actuator/health -UseBasicParsing | Out-Null }
# Then in Grafana Explore (Prometheus):  http_server_requests_seconds_count{uri="/actuator/health"}
# And in Grafana Explore (Loki):         {job=~".+-service"} |= "Started"
```

### Scorecard addition

| # | Test | Expected | Rubric |
|---|------|----------|--------|
| 11a | Grafana login + dashboards | UI loads | Observability |
| 11b | /actuator/prometheus | metric lines | Prometheus integration |
| 11c | Loki labels list | JSON with labels | Log aggregation |
| 11d | Metrics + logs after requests | count rises, logs appear | E2E observability |
