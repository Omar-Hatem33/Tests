package com.team21.uber.payment.repository;

import com.team21.uber.payment.model.Payment;
import com.team21.uber.payment.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    boolean existsByRideIdAndStatus(Long rideId, PaymentStatus status);

    @Query("SELECT p FROM Payment p " +
            "LEFT JOIN FETCH p.paymentCoupons pc " +
            "LEFT JOIN FETCH pc.coupon c " +
            "WHERE p.id = :id")
    Optional<Payment> findByIdWithCoupons(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.status = :status AND p.createdAt BETWEEN :start AND :end")
    Double sumAmountByStatusAndDateRange(@Param("status") PaymentStatus status,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(p) FROM Payment p " +
            "WHERE p.status = :status AND p.createdAt BETWEEN :start AND :end")
    Long countByStatusAndDateRange(@Param("status") PaymentStatus status,
                                   @Param("start") LocalDateTime start,
                                   @Param("end") LocalDateTime end);

    @Query(value = "SELECT COUNT(*) FROM users WHERE id = :userId", nativeQuery = true)
    Long countUsersById(@Param("userId") Long userId);

    @Query(value = """
        SELECT method, COUNT(*) as count, SUM(amount) as total
        FROM payments
        WHERE user_id = :userId AND status = 'COMPLETED'
        GROUP BY method
        """, nativeQuery = true)
    List<Object[]> getCompletedPaymentsByMethod(@Param("userId") Long userId);

    @Query(value = """
    SELECT * FROM payments
    WHERE (CAST(:start AS timestamp) IS NULL OR created_at >= CAST(:start AS timestamp))
      AND (CAST(:end AS timestamp) IS NULL OR created_at <= CAST(:end AS timestamp))
      AND (CAST(:status AS text) IS NULL OR status::text = CAST(:status AS text))
      AND (CAST(:userId AS bigint) IS NULL OR user_id = CAST(:userId AS bigint))
    ORDER BY created_at DESC
    """, nativeQuery = true)
    List<Payment> searchByStatusAndDateRange(
            @Param("status") String status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("userId") Long userId);

    @Query(value = "SELECT status FROM rides WHERE id = :rideId", nativeQuery = true)
    String findRideStatusById(@Param("rideId") Long rideId);

    @Query(value = "SELECT metadata::text FROM rides WHERE id = :rideId", nativeQuery = true)
    String findRideMetadataById(@Param("rideId") Long rideId);

    @Query(value = "SELECT fare, user_id FROM rides WHERE id = :rideId", nativeQuery = true)
    List<Object[]> findRideDetailsById(@Param("rideId") Long rideId);

    @Query(value = "SELECT * FROM payments WHERE ride_id = :rideId AND status = 'PENDING' LIMIT 1",
            nativeQuery = true)
    Optional<Payment> findByRideIdNative(@Param("rideId") Long rideId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE payments
        SET status = 'COMPLETED',
            method = :method,
            transaction_details = CAST(:transactionDetails AS jsonb)
        WHERE ride_id = :rideId AND status = 'PENDING'
        """, nativeQuery = true)
    int processPaymentNative(@Param("rideId") Long rideId,
                             @Param("method") String method,
                             @Param("transactionDetails") String transactionDetails);

    @Query(value = """
        SELECT
            d.vehicle_details ->> 'vehicleType'                              AS vehicleType,
            SUM(
                CASE
                    WHEN jsonb_exists(p.transaction_details, 'surgeFee')
                        THEN (p.transaction_details ->> 'surgeFee')::numeric
                    ELSE p.amount * 0.15
                END
            )                                                                AS surgeFeeRevenue,
            SUM(p.amount) - SUM(
                CASE
                    WHEN jsonb_exists(p.transaction_details, 'surgeFee')
                        THEN (p.transaction_details ->> 'surgeFee')::numeric
                    ELSE p.amount * 0.15
                END
            )                                                                AS baseFareRevenue,
            SUM(p.amount)                                                    AS totalRevenue,
            COUNT(DISTINCT p.ride_id)                                        AS rideCount
        FROM  payments  p
        JOIN  rides     r ON r.id       = p.ride_id
        JOIN  drivers   d ON d.id       = r.driver_id
        WHERE r.requested_at BETWEEN :start AND :end
          AND p.status = 'COMPLETED'
        GROUP BY d.vehicle_details ->> 'vehicleType'
        """, nativeQuery = true)
    List<Object[]> findFareRevenueByVehicleType(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}