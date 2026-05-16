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
    // KEPT   : updateDriverStatus    — still used by S3-F7 cancelRide until that is refactored

    // S3-F2 / S3-F7: update driver status (cross-service native SQL)
    // TODO(S3-F7): remove once cancelRide is refactored to publish ride.cancelled event
    @Modifying
    @Transactional
    @Query(value = "UPDATE drivers SET status = :status WHERE id = :driverId", nativeQuery = true)
    void updateDriverStatus(@Param("driverId") Long driverId,
                            @Param("status") String status);

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

    // S3-F7
    @Modifying
    @Query(value = "UPDATE drivers SET status = 'AVAILABLE' WHERE id = :driverId", nativeQuery = true)
    void updateDriverStatusToAvailable(@Param("driverId") Long driverId);

    // ── S3-F11 ──────────────────────────────────────────
    // REMOVED: findDriverNameById       — M3 uses Feign → driver-service GET /api/drivers/{id}
    // REMOVED: findDriverVehicleTypeById — M3 uses Feign → driver-service GET /api/drivers/{id}

    // KEPT: findUserNameById — still used by findUserName() for S3-F12 user existence check
    // TODO(S3-F12): remove once getRecommendations is refactored to use Feign → user-service
    @Query(value = "SELECT name FROM users WHERE id = :userId", nativeQuery = true)
    String findUserNameById(@Param("userId") Long userId);
}

