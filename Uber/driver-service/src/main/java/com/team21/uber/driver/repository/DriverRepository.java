package com.team21.uber.driver.repository;

import com.team21.uber.driver.model.Driver;
import com.team21.uber.driver.model.DriverStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    // S2-F1: Search by status + rating range
    List<Driver> findByStatusAndRatingBetweenOrderByRatingDesc(
            DriverStatus status,
            Double min,
            Double max
    );

    List<Driver> findByRatingBetweenOrderByRatingDesc(
            Double min,
            Double max
    );

    //S2-F3: Driver earnings
    @Query(value = """
        SELECT COUNT(*) AS total_rides,
               COALESCE(SUM(fare), 0),
               COALESCE(AVG(fare), 0)
        FROM rides
        WHERE driver_id = :driverId
          AND status = 'COMPLETED'
          AND completed_at BETWEEN :startDate AND :endDate
    """, nativeQuery = true)
    List<Object[]> getDriverEarnings(
            @Param("driverId") Long driverId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // S2-F6: Top rated drivers
    @Query(value = """
        SELECT d.id, d.name, d.rating, COUNT(r.id) as total_rides
        FROM drivers d
        LEFT JOIN rides r ON d.id = r.driver_id AND r.status = 'COMPLETED'
        GROUP BY d.id, d.name, d.rating
        ORDER BY d.rating DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> findTopRatedDrivers(@Param("limit") int limit);

    //S2-F7: Get user role
    @Query(value = """
        SELECT role
        FROM users
        WHERE id = :userId
    """, nativeQuery = true)
    String getUserRole(@Param("userId") Long userId);

    //S2-F12: Driver Performance Dashboard
    @Query(value = """
        SELECT COUNT(r.id), COALESCE(SUM(p.amount), 0), COALESCE(AVG(p.amount), 0)
        FROM rides r
        LEFT JOIN payments p ON p.ride_id = r.id
        WHERE r.driver_id = :driverId AND r.status = 'COMPLETED'
        """, nativeQuery = true)
    List<Object[]> getDriverRideStats(@Param("driverId") Long driverId);

    // Atomic rating update to avoid lost-write race
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE drivers
           SET rating = ((COALESCE(rating, 0) * COALESCE(total_ratings, 0)) + :newRating)
                        / (COALESCE(total_ratings, 0) + 1),
               total_ratings = COALESCE(total_ratings, 0) + 1
         WHERE id = :driverId
    """, nativeQuery = true)
    int applyRating(@Param("driverId") Long driverId, @Param("newRating") double newRating);

    // S2-F5: Filter by vehicle type (JSONB)
    @Query(value = """
        SELECT * FROM drivers
        WHERE vehicle_details ->> 'vehicleType' = :type
        AND (:status IS NULL OR status = :status)
        ORDER BY rating DESC
    """, nativeQuery = true)
    List<Driver> findByVehicleTypeAndOptionalStatus(
            @Param("type") String type,
            @Param("status") String status
    );
   

}