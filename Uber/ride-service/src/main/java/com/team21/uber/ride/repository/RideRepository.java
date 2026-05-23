package com.team21.uber.ride.repository;

import com.team21.uber.ride.model.Ride;
import com.team21.uber.ride.model.RideStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RideRepository — local-only persistence on ride-postgres.
 *
 * All cross-service native SQL has been removed across the M3 refactors:
 *   - findDriverStatusById      → S3-F2 uses Feign → driver-service
 *   - updateDriverStatus        → S3-F7 publishes ride.cancelled
 *   - setDriverAvailable        → S3-F4 publishes ride.completed
 *   - createPendingPayment      → S3-F4 publishes ride.completed
 *   - findUserNameById          → S3-F11/F12 use Feign → user-service
 *   - findDriverNameById        → S3-F11 uses Feign → driver-service
 *   - findDriverVehicleTypeById → S3-F11 uses Feign → driver-service
 *
 * What's added in this slice (S3-read-endpoints):
 *   - countByUserIdAndStatusIn  (Spring Data derived)        → S1-F4 active-count, S1-F9 completed-count
 *   - countByDriverIdAndStatusIn (Spring Data derived)       → S2-F4 active-count, S2-F6 completed-count
 *   - getUserRideSummary  (native aggregate)                 → S1-F3 summary
 *   - getDriverRideSummary (native aggregate, optional dates) → S2-F3, S2-F12 summary
 */
@Repository
public interface RideRepository extends JpaRepository<Ride, Long> {

    // ── S3-F1: search rides ──────────────────────────────────────────────────
    List<Ride> findByRequestedAtBetweenOrderByRequestedAtDesc(
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    List<Ride> findByStatusAndRequestedAtBetweenOrderByRequestedAtDesc(
            RideStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    boolean existsByUserIdAndStatusIn(Long userId, List<RideStatus> statuses);

    // ── S3-F3: count nearby active rides for surge pricing ──────────────────
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

    // ── S3-F5: filter by metadata ────────────────────────────────────────────
    @Query(value = "SELECT * FROM rides WHERE metadata ->> :key = :value", nativeQuery = true)
    List<Ride> findByMetadataField(@Param("key") String key, @Param("value") String value);

    // ── S3-F6: analytics aggregation ─────────────────────────────────────────
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

    // ── S3-read-endpoints: Feign-facing aggregate queries ────────────────────

    /**
     * Used by S1-F4 (active-count) and S1-F9 (completed-count).
     * Status list is supplied by the caller (RideQueryService).
     */
    long countByUserIdAndStatusIn(Long userId, List<RideStatus> statuses);

    /**
     * Used by S2-F4 (active-count) and S2-F6 (completed-count).
     * Status list is supplied by the caller (RideQueryService).
     */
    long countByDriverIdAndStatusIn(Long driverId, List<RideStatus> statuses);

    /**
     * Used by S1-F3 (user ride summary).
     * Returns one row: [totalRides, completedRides, cancelledRides, totalSpent, averageFare].
     *   completedRides / totalSpent / averageFare are computed over status IN ('COMPLETED', 'PAID').
     *   cancelledRides is computed over status = 'CANCELLED'.
     *   totalRides counts every status.
     */
    @Query(value = """
            SELECT
                COUNT(*) AS totalRides,
                COALESCE(SUM(CASE WHEN status IN ('COMPLETED', 'PAID') THEN 1 ELSE 0 END), 0) AS completedRides,
                COALESCE(SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END), 0) AS cancelledRides,
                COALESCE(SUM(CASE WHEN status IN ('COMPLETED', 'PAID') THEN fare ELSE 0 END), 0) AS totalSpent,
                COALESCE(AVG(CASE WHEN status IN ('COMPLETED', 'PAID') THEN fare END), 0) AS averageFare
            FROM rides
            WHERE user_id = :userId
            """, nativeQuery = true)
    List<Object[]> getUserRideSummary(@Param("userId") Long userId);

    /**
     * Used by S2-F3 (driver earnings) and S2-F12 (driver dashboard).
     * Returns one row: [totalRides, totalEarnings, averageFare] for status IN ('COMPLETED', 'PAID')
     * filtered by completed_at within [startDate, endDate]. When no date range is requested,
     * the caller passes sentinel values (FAR_PAST / FAR_FUTURE) — this keeps the query
     * branch-free and avoids PostgreSQL null-parameter type-inference issues.
     */
    @Query(value = """
            SELECT
                COUNT(*) AS totalRides,
                COALESCE(SUM(fare), 0) AS totalEarnings,
                COALESCE(AVG(fare), 0) AS averageFare
            FROM rides
            WHERE driver_id = :driverId
              AND status IN ('COMPLETED', 'PAID')
              AND completed_at >= :startDate
              AND completed_at <= :endDate
            """, nativeQuery = true)
    List<Object[]> getDriverRideSummary(@Param("driverId") Long driverId,
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);
}