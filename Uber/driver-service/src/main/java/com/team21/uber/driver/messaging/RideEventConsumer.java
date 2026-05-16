package com.team21.uber.driver.messaging;

import com.team21.uber.driver.config.RabbitMQConfig;
import com.team21.uber.driver.model.Driver;
import com.team21.uber.driver.model.DriverStatus;
import com.team21.uber.driver.repository.DriverRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Consumes ride lifecycle events from ride.events exchange.
 *
 * ride.placed    → set driver BUSY (no-op + WARN if already BUSY)
 * ride.completed → set driver AVAILABLE; increment completed-ride stats
 * ride.cancelled → set driver AVAILABLE; reverse stats if previously incremented
 *
 * Auto ACK is configured in RabbitMQConfig.
 * On failure (uncaught exception) the message is routed to driver.ride.saga-listener.dlq
 * via x-dead-letter-routing-key (defaultRequeueRejected=false).
 */
@Component
public class RideEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RideEventConsumer.class);

    private final DriverRepository driverRepository;

    public RideEventConsumer(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.DRIVER_RIDE_SAGA_QUEUE)
    @Transactional
    public void handleRideEvent(
            Map<String, Object> payload,
            @Header("amqp_receivedRoutingKey") String routingKey) {

        log.info("RideEventConsumer received event routingKey={} payload={}", routingKey, payload);

        try {
            switch (routingKey) {
                case RabbitMQConfig.RIDE_PLACED_KEY    -> handleRidePlaced(payload);
                case RabbitMQConfig.RIDE_COMPLETED_KEY -> handleRideCompleted(payload);
                case RabbitMQConfig.RIDE_CANCELLED_KEY -> handleRideCancelled(payload);
                default -> log.warn("Unknown routing key: {}", routingKey);
            }
        } catch (Exception e) {
            log.error("RideEventConsumer failed for routingKey={}: {}", routingKey, e.getMessage(), e);
            throw e; // rethrow so message goes to DLQ
        }
    }

    // ── ride.placed → flip driver to BUSY ────────────────────────

    private void handleRidePlaced(Map<String, Object> payload) {
        Long driverId = extractLong(payload, "driverId");
        if (driverId == null) {
            log.warn("ride.placed: missing driverId in payload {}", payload);
            return;
        }

        Optional<Driver> opt = driverRepository.findById(driverId);
        if (opt.isEmpty()) {
            log.warn("ride.placed: driver {} not found — skipping", driverId);
            return;
        }

        Driver driver = opt.get();
        if (driver.getStatus() == DriverStatus.BUSY) {
            // Concurrent assignment race — another event already flipped this driver
            log.warn("ride.placed: driver {} already BUSY — no-op (concurrent assignment)", driverId);
            return;
        }

        driver.setStatus(DriverStatus.BUSY);
        driverRepository.save(driver);
        log.info("ride.placed: driver {} set to BUSY", driverId);
    }

    // ── ride.completed → flip driver to AVAILABLE ────────────────

    private void handleRideCompleted(Map<String, Object> payload) {
        Long driverId = extractLong(payload, "driverId");
        if (driverId == null) {
            log.warn("ride.completed: missing driverId in payload {}", payload);
            return;
        }

        Optional<Driver> opt = driverRepository.findById(driverId);
        if (opt.isEmpty()) {
            log.warn("ride.completed: driver {} not found — skipping", driverId);
            return;
        }

        Driver driver = opt.get();
        driver.setStatus(DriverStatus.AVAILABLE);

        // Increment total ratings as a proxy for completed rides (M1 convention)
        if (driver.getTotalRatings() == null) driver.setTotalRatings(0);
        driver.setTotalRatings(driver.getTotalRatings() + 1);

        driverRepository.save(driver);
        log.info("ride.completed: driver {} set to AVAILABLE, totalRatings={}", driverId, driver.getTotalRatings());
    }

    // ── ride.cancelled → flip driver to AVAILABLE ────────────────

    private void handleRideCancelled(Map<String, Object> payload) {
        Long driverId = extractLong(payload, "driverId");
        if (driverId == null) {
            log.warn("ride.cancelled: missing driverId in payload {}", payload);
            return;
        }

        Optional<Driver> opt = driverRepository.findById(driverId);
        if (opt.isEmpty()) {
            log.warn("ride.cancelled: driver {} not found — skipping", driverId);
            return;
        }

        Driver driver = opt.get();

        // Only set AVAILABLE if driver was actually BUSY (guarding against duplicate events)
        if (driver.getStatus() == DriverStatus.BUSY) {
            driver.setStatus(DriverStatus.AVAILABLE);
        }

        // Reverse incremented stats if the ride was previously counted as completed
        Boolean wasCompleted = extractBoolean(payload, "wasCompleted");
        if (Boolean.TRUE.equals(wasCompleted) && driver.getTotalRatings() != null
                && driver.getTotalRatings() > 0) {
            driver.setTotalRatings(driver.getTotalRatings() - 1);
        }

        driverRepository.save(driver);
        log.info("ride.cancelled: driver {} set to AVAILABLE", driverId);
    }

    // ── Helpers ───────────────────────────────────────────────────

    private Long extractLong(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val == null) return null;
        try { return ((Number) val).longValue(); }
        catch (ClassCastException e) {
            try { return Long.parseLong(val.toString()); }
            catch (NumberFormatException ex) { return null; }
        }
    }

    private Boolean extractBoolean(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val == null) return null;
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(val.toString());
    }
}
