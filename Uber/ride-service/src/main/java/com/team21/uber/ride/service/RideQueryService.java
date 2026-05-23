package com.team21.uber.ride.service;

import com.team21.uber.contracts.dto.DriverRideSummaryDTO;
import com.team21.uber.contracts.dto.RideSummaryDTO;
import com.team21.uber.ride.model.RideStatus;
import com.team21.uber.ride.repository.RideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * RideQueryService — aggregate read endpoints exposed to other services via Feign.
 *
 * These methods are pure read queries on the local ride-postgres database; they do
 * NOT call any other service. They are consumed by:
 *   - user-service     (S1-F3 ride summary, S1-F4 active count, S1-F9 completed count)
 *   - driver-service   (S2-F3 earnings summary, S2-F4 active count, S2-F6 / S2-F12 completed count)
 *
 * Status sets per M3 §5 (Ride Service Refactoring):
 *   USER  active     : REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, PAYMENT_PENDING
 *   USER  completed  : COMPLETED, PAID
 *   DRIVER active    : ACCEPTED, IN_PROGRESS, COMPLETED, PAYMENT_PENDING
 *   DRIVER completed : COMPLETED, PAID
 *
 * Sentinel timestamps are used (instead of nullable parameters) when no date range is
 * supplied, so the native query does not need PostgreSQL CAST tricks for null inference.
 */
@Service
public class RideQueryService {

    private static final Logger log = LoggerFactory.getLogger(RideQueryService.class);

    // Sentinel range used when no startDate/endDate is supplied (all-time stats).
    private static final LocalDateTime FAR_PAST   = LocalDateTime.of(1900, 1, 1, 0, 0);
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2200, 1, 1, 0, 0);

    private static final List<RideStatus> USER_ACTIVE_STATUSES = List.of(
            RideStatus.REQUESTED,
            RideStatus.ACCEPTED,
            RideStatus.IN_PROGRESS,
            RideStatus.COMPLETED,
            RideStatus.PAYMENT_PENDING
    );

    private static final List<RideStatus> USER_COMPLETED_STATUSES = List.of(
            RideStatus.COMPLETED,
            RideStatus.PAID
    );

    private static final List<RideStatus> DRIVER_ACTIVE_STATUSES = List.of(
            RideStatus.ACCEPTED,
            RideStatus.IN_PROGRESS,
            RideStatus.COMPLETED,
            RideStatus.PAYMENT_PENDING
    );

    private static final List<RideStatus> DRIVER_COMPLETED_STATUSES = List.of(
            RideStatus.COMPLETED,
            RideStatus.PAID
    );

    private final RideRepository rideRepository;

    public RideQueryService(RideRepository rideRepository) {
        this.rideRepository = rideRepository;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // USER-FACING AGGREGATES (called by user-service)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Used by S1-F3 (GET /api/users/{id}/ride-summary).
     *
     * Returns aggregate stats across ALL rides for this user:
     *   totalRides     — count of any-status rides
     *   completedRides — count where status IN (COMPLETED, PAID)
     *   cancelledRides — count where status = CANCELLED
     *   totalSpent     — sum of fares where status IN (COMPLETED, PAID)
     *   averageFare    — avg of fares where status IN (COMPLETED, PAID)
     *
     * A user with zero rides returns the zero-valued empty DTO (NOT 404).
     */
    public RideSummaryDTO getUserRideSummary(Long userId) {
        log.info("getUserRideSummary userId={}", userId);
        List<Object[]> rows = rideRepository.getUserRideSummary(userId);
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return RideSummaryDTO.empty();
        }
        Object[] r = rows.get(0);
        long totalRides     = ((Number) r[0]).longValue();
        long completedRides = ((Number) r[1]).longValue();
        long cancelledRides = ((Number) r[2]).longValue();
        double totalSpent   = ((Number) r[3]).doubleValue();
        double averageFare  = ((Number) r[4]).doubleValue();
        log.info("getUserRideSummary userId={} total={} completed={} cancelled={} spent={} avg={}",
                userId, totalRides, completedRides, cancelledRides, totalSpent, averageFare);
        return new RideSummaryDTO((int) totalRides, (int) completedRides, (int) cancelledRides, totalSpent, averageFare);
    }

    /**
     * Used by S1-F4 (PUT /api/users/{id}/deactivate pre-check).
     * Counts user rides with status IN (REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, PAYMENT_PENDING).
     */
    public int getUserActiveRideCount(Long userId) {
        long count = rideRepository.countByUserIdAndStatusIn(userId, USER_ACTIVE_STATUSES);
        log.info("getUserActiveRideCount userId={} count={}", userId, count);
        return (int) count;
    }

    /**
     * Used by S1-F9.
     * Counts user rides with status IN (COMPLETED, PAID).
     */
    public long getUserCompletedRideCount(Long userId) {
        long count = rideRepository.countByUserIdAndStatusIn(userId, USER_COMPLETED_STATUSES);
        log.info("getUserCompletedRideCount userId={} count={}", userId, count);
        return count;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DRIVER-FACING AGGREGATES (called by driver-service)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Used by S2-F3 (GET /api/drivers/{id}/earnings) and S2-F12 (driver dashboard).
     *
     * Returns aggregate earnings for COMPLETED + PAID rides assigned to this driver,
     * optionally filtered by completedAt date range. Both startDate and endDate are
     * optional; when omitted, all-time stats are returned (sentinel range applied).
     *
     * Date format accepted: ISO-LOCAL-DATE (yyyy-MM-dd) OR ISO-LOCAL-DATE-TIME.
     */
    public DriverRideSummaryDTO getDriverRideSummary(Long driverId, String startDate, String endDate) {
        LocalDateTime start = parseStartDate(startDate);
        LocalDateTime end   = parseEndDate(endDate);
        log.info("getDriverRideSummary driverId={} start={} end={}", driverId, start, end);

        List<Object[]> rows = rideRepository.getDriverRideSummary(driverId, start, end);
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return DriverRideSummaryDTO.empty();
        }
        Object[] r = rows.get(0);
        long totalRides       = ((Number) r[0]).longValue();
        double totalEarnings  = ((Number) r[1]).doubleValue();
        double averageFare    = ((Number) r[2]).doubleValue();
        log.info("getDriverRideSummary driverId={} total={} earnings={} avg={}",
                driverId, totalRides, totalEarnings, averageFare);
        return new DriverRideSummaryDTO(totalRides, totalEarnings, averageFare);
    }

    /**
     * Used by S2-F4 (PUT /api/drivers/{id}/availability OFFLINE pre-check).
     * Counts driver rides with status IN (ACCEPTED, IN_PROGRESS, COMPLETED, PAYMENT_PENDING).
     */
    public int getDriverActiveRideCount(Long driverId) {
        long count = rideRepository.countByDriverIdAndStatusIn(driverId, DRIVER_ACTIVE_STATUSES);
        log.info("getDriverActiveRideCount driverId={} count={}", driverId, count);
        return (int) count;
    }

    /**
     * Used by S2-F6 (top-rated-drivers report).
     * Counts driver rides with status IN (COMPLETED, PAID).
     */
    public long getDriverCompletedRideCount(Long driverId) {
        long count = rideRepository.countByDriverIdAndStatusIn(driverId, DRIVER_COMPLETED_STATUSES);
        log.info("getDriverCompletedRideCount driverId={} count={}", driverId, count);
        return count;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Date parsing helpers
    // ──────────────────────────────────────────────────────────────────────────

    private LocalDateTime parseStartDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return FAR_PAST;
        try {
            return LocalDate.parse(dateStr).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(dateStr);
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid startDate format (expected yyyy-MM-dd or ISO datetime): " + dateStr);
            }
        }
    }

    private LocalDateTime parseEndDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return FAR_FUTURE;
        try {
            return LocalDate.parse(dateStr).atTime(LocalTime.MAX);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(dateStr);
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid endDate format (expected yyyy-MM-dd or ISO datetime): " + dateStr);
            }
        }
    }
}
