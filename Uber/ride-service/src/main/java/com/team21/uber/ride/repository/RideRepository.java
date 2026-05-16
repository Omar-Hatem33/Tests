package com.team21.uber.ride.repository;

import com.team21.uber.ride.model.Ride;
import com.team21.uber.ride.model.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {

    // ── S3-F1 ──────────────────────────────────────────
    List<Ride> findByRequestedAtBetweenOrderByRequestedAtDesc(
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    List<Ride> findByStatusAndRequestedAtBetweenOrderByRequestedAtDesc(
            RideStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    boolean existsByUserIdAndStatusIn(Long userId, java.util.List<RideStatus> statuses);

    // ── S3-F2 ──────────────────────────────────────────
    // REMOVED: findDriverStatusById  — M3 uses Feign → driver-service GET /api/drivers/{id}
    // REMOVED: updateDriverStatus    — M3 publishes ride.cancelled event; driver-service consumes and updates

    // S3-F3: count nearby active rides for surge pricing
    @Query(value = """
    SELECT COUNT(*) FROM rides
    WHERE status IN ('REQUESTED', 'ACCEPTED', 'IN_PROGRESS')
    AND pickup_latitude BETWEEN :latMin AND :latMax
    AND pickup_longitude BETWEEN :lonMin AND :lonMax
    """, nativeQuery = true)
    int countNearbyActiveRides(
            @Param("latMin") double latMin,
            @Param("latMax") double latMax,
            @Param("lonMin") double lonMin,
            @Param("lonMax") double lonMax
    );

    // ── S3-F4 ──────────────────────────────────────────
    // REMOVED: setDriverAvailable — M3 publishes ride.completed event; driver-service consumes and updates
    // REMOVED: createPendingPayment — M3 publishes ride.completed event; payment-service consumes and creates

    // ── S3-F5 ──────────────────────────────────────────

    @Query(value = "SELECT * FROM rides WHERE metadata ->> :key = :value", nativeQuery = true)
    List<Ride> findByMetadataField(@Param("key") String key, @Param("value") String value);

    // ── S3-F6 ──────────────────────────────────────────
    @Query(value = """
    SELECT
        COUNT(*) AS totalRides,
        COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completedRides,
        COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelledRides,
        COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN fare ELSE 0 END), 0) AS totalRevenue,
        COALESCE(AVG(CASE WHEN status = 'COMPLETED' THEN fare END), 0) AS averageFare
    FROM rides
    WHERE requested_at BETWEEN :startDate AND :endDate
    """, nativeQuery = true)
    List<Object[]> getRideAnalytics(@Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);

    // S3-F7 — REMOVED: updateDriverStatusToAvailable
    // Driver re-availability happens asynchronously when driver-service consumes ride.cancelled

    // ── S3-EVENTS: New read-only endpoints for S1 and S2 teams ───────────

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

    // ── S3-F11 ──────────────────────────────────────────
    // REMOVED: findDriverNameById       — M3 uses Feign → driver-service GET /api/drivers/{id}
    // REMOVED: findDriverVehicleTypeById — M3 uses Feign → driver-service GET /api/drivers/{id}

    // KEPT: findUserNameById — still used by findUserName() for S3-F12 user existence check
    // TODO(S3-F12): remove once getRecommendations is refactored to use Feign → user-service
    @Query(value = "SELECT name FROM users WHERE id = :userId", nativeQuery = true)
    String findUserNameById(@Param("userId") Long userId);
}

