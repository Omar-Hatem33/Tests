package com.team21.uber.ride.saga;

import com.team21.uber.contracts.dto.DriverDTO;
import com.team21.uber.contracts.dto.UserDTO;
import com.team21.uber.contracts.events.RideCompletedEvent;
import com.team21.uber.contracts.feign.DriverServiceClient;
import com.team21.uber.contracts.feign.LocationServiceClient;
import com.team21.uber.contracts.feign.UserServiceClient;
import com.team21.uber.ride.messaging.publishers.RideEventPublisher;
import com.team21.uber.ride.model.Ride;
import com.team21.uber.ride.repository.RideRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * SagaTriggerService — owns the S3-F4 saga entry point for ride completion.
 *
 * Placed in the {@code saga} package per §12 spec:
 *   saga/ = saga-specific consumers + state transitions
 *
 * Responsibilities:
 *   1. Three Feign pre-checks (user ACTIVE, driver BUSY, recent GPS ping)
 *   2. Save the prepared Ride entity (status already set to COMPLETED by caller)
 *   3. Publish {@code ride.completed} to kick off the payment + driver saga
 *
 * Called by {@link com.team21.uber.ride.service.RideService#completeRide(Long)}
 * after the ride entity has been prepared (fare set, status=COMPLETED,
 * completedAt=now) but before it is persisted. If any pre-check fails this
 * method throws — the ride is NOT saved and stays IN_PROGRESS.
 *
 * The entire method runs in a single transaction with the caller (Spring
 * propagation=REQUIRED). If the publish throws after save, the transaction
 * rolls back — the saga is not started and the ride stays IN_PROGRESS.
 */
@Service
public class SagaTriggerService {

    private static final Logger log = LoggerFactory.getLogger(SagaTriggerService.class);

    private final RideRepository rideRepository;
    private final UserServiceClient userServiceClient;
    private final DriverServiceClient driverServiceClient;
    private final LocationServiceClient locationServiceClient;
    private final RideEventPublisher rideEventPublisher;

    public SagaTriggerService(RideRepository rideRepository,
                              UserServiceClient userServiceClient,
                              DriverServiceClient driverServiceClient,
                              LocationServiceClient locationServiceClient,
                              RideEventPublisher rideEventPublisher) {
        this.rideRepository = rideRepository;
        this.userServiceClient = userServiceClient;
        this.driverServiceClient = driverServiceClient;
        this.locationServiceClient = locationServiceClient;
        this.rideEventPublisher = rideEventPublisher;
    }

    /**
     * S3-F4 saga trigger. Receives a prepared (not yet persisted) Ride
     * with status=COMPLETED and fare set. Runs three Feign pre-checks,
     * then saves and publishes.
     *
     * @param ride prepared ride — status=COMPLETED, completedAt set, fare set
     * @return the saved Ride
     * @throws ResponseStatusException 400 if any pre-check fails, 503 if a
     *         downstream service is unavailable
     */
    @Transactional
    public Ride triggerRideCompletion(Ride ride) {
        Long userId   = ride.getUserId();
        Long driverId = ride.getDriverId();

        // ── Pre-check #1: user must be ACTIVE ────────────────────────────────
        log.info("S3-F4 pre-check #1: Calling UserServiceClient.getUser userId={}", userId);
        UserDTO userDTO;
        try {
            userDTO = userServiceClient.getUser(userId);
            log.info("S3-F4 pre-check #1: userId={} status={}", userId, userDTO.status());
        } catch (FeignException.NotFound e) {
            log.warn("S3-F4 pre-check #1 failed: user {} not found", userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User not found");
        } catch (FeignException e) {
            log.error("S3-F4 pre-check #1 failed: user-service unavailable userId={}: {}",
                    userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "User service temporarily unavailable");
        }
        if (!"ACTIVE".equalsIgnoreCase(userDTO.status())) {
            log.warn("S3-F4 pre-check #1 failed: userId={} status={} expected ACTIVE",
                    userId, userDTO.status());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User is not active (status=" + userDTO.status() + ")");
        }

        // ── Pre-check #2: driver must be BUSY ────────────────────────────────
        log.info("S3-F4 pre-check #2: Calling DriverServiceClient.getDriver driverId={}", driverId);
        DriverDTO driverDTO;
        try {
            driverDTO = driverServiceClient.getDriver(driverId);
            log.info("S3-F4 pre-check #2: driverId={} status={}", driverId, driverDTO.status());
        } catch (FeignException.NotFound e) {
            log.warn("S3-F4 pre-check #2 failed: driver {} not found", driverId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Driver not found");
        } catch (FeignException e) {
            log.error("S3-F4 pre-check #2 failed: driver-service unavailable driverId={}: {}",
                    driverId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Driver service temporarily unavailable");
        }
        if (!"BUSY".equalsIgnoreCase(driverDTO.status())) {
            log.warn("S3-F4 pre-check #2 failed: driverId={} status={} expected BUSY",
                    driverId, driverDTO.status());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Driver is not active for this ride (status=" + driverDTO.status() + ")");
        }

        // ── Pre-check #3: driver must have a recent GPS ping ─────────────────
        log.info("S3-F4 pre-check #3: Calling LocationServiceClient.getRecentLocationForDriver driverId={}",
                driverId);
        try {
            locationServiceClient.getRecentLocationForDriver(driverId);
            log.info("S3-F4 pre-check #3: driverId={} has recent GPS ping", driverId);
        } catch (FeignException.NotFound e) {
            log.warn("S3-F4 pre-check #3 failed: no recent location ping for driver {}", driverId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Driver not actively tracked (no recent GPS ping)");
        } catch (FeignException e) {
            log.error("S3-F4 pre-check #3 failed: location-service unavailable driverId={}: {}",
                    driverId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Location service temporarily unavailable");
        }

        // ── All pre-checks passed — persist COMPLETED ride ───────────────────
        Ride saved = rideRepository.save(ride);
        log.info("S3-F4: ride {} saved as COMPLETED fare={}", saved.getId(), saved.getFare());

        // ── Publish ride.completed — triggers payment + driver sagas ─────────
        rideEventPublisher.publishRideCompleted(
                new RideCompletedEvent(saved.getId(), userId, driverId, saved.getFare())
        );
        log.info("S3-F4: published ride.completed for ride {}", saved.getId());

        return saved;
    }
}