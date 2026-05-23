package com.team21.uber.ride.saga;

import com.team21.uber.contracts.events.RideCancelledEvent;
import com.team21.uber.ride.config.RideEventConfig;
import com.team21.uber.ride.messaging.publishers.RideEventPublisher;
import com.team21.uber.ride.model.Ride;
import com.team21.uber.ride.model.RideStatus;
import com.team21.uber.ride.repository.RideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PaymentEventConsumer — listens on the {@code ride.saga-feedback} queue for
 * payment lifecycle events published by payment-service on the
 * {@code payment.events} exchange, and applies the corresponding ride-status
 * transitions.
 *
 * Saga state machine (consumer perspective):
 *
 *   COMPLETED        --payment.initiated-->  PAYMENT_PENDING
 *   PAYMENT_PENDING  --payment.completed-->  PAID
 *   PAYMENT_PENDING  --payment.failed---->   PAYMENT_FAILED
 *                                            └─ publishes ride.cancelled
 *                                               (compensation trigger)
 *   PAYMENT_FAILED   --payment.refunded-->   REFUNDED
 *   PAID             --payment.refunded-->   REFUNDED   (manual refund path)
 *
 * Idempotency contract — every handler:
 *   1. Loads the ride (no-op + warn if missing — events for unknown rides are
 *      acked silently rather than DLQ'd, since the data may have been pruned).
 *   2. Checks if the ride is already in the target state — no-op if so (this
 *      makes redelivered messages safe).
 *   3. Checks if the ride is in an allowed source state — no-op + warn if not
 *      (a misordered event is also acked rather than DLQ'd; the DLQ is reserved
 *      for true deserialization or processing failures).
 *   4. Performs the state transition and persists. Side effects (publishing
 *      ride.cancelled on payment.failed) only fire when an actual transition
 *      occurred — never on idempotent no-op paths.
 *
 * Message payloads are consumed as {@code Map<String,Object>} rather than the
 * typed {@code PaymentInitiatedEvent}/{@code PaymentCompletedEvent}/etc. records
 * from contracts. This avoids relying on {@code __TypeId__} headers that
 * payment-service may or may not set — dispatch is driven purely by the
 * RabbitMQ routing key header.
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final RideRepository rideRepository;
    private final RideEventPublisher rideEventPublisher;

    public PaymentEventConsumer(RideRepository rideRepository,
                                RideEventPublisher rideEventPublisher) {
        this.rideRepository = rideRepository;
        this.rideEventPublisher = rideEventPublisher;
    }

    /**
     * Single entry point for all four payment routing keys. Dispatches on the
     * {@code amqp_receivedRoutingKey} header. Each handler runs in its own
     * transaction so a failure in one event doesn't roll back state set by a
     * previous successful event on the same consumer thread.
     */
    @RabbitListener(queues = RideEventConfig.RIDE_SAGA_FEEDBACK_QUEUE)
    public void onPaymentEvent(
            @Payload Map<String, Object> payload,
            @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey,
            @Header(name = "X-Correlation-ID", required = false) String correlationId) {

        String mdcCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        MDC.put("correlationId", mdcCorrelationId);
        MDC.put("routingKey", routingKey == null ? "" : routingKey);

        try {
            Long rideId    = extractLong(payload, "rideId");
            Long paymentId = extractLong(payload, "paymentId");
            if (rideId != null)    MDC.put("rideId", String.valueOf(rideId));
            if (paymentId != null) MDC.put("paymentId", String.valueOf(paymentId));

            log.info("Received payment event routingKey={} rideId={} paymentId={}",
                    routingKey, rideId, paymentId);

            if (rideId == null) {
                log.error("Payment event missing rideId — payload={}, acknowledging without action", payload);
                return;
            }
            if (routingKey == null) {
                log.error("Payment event missing routing key — rideId={}, acknowledging without action", rideId);
                return;
            }

            switch (routingKey) {
                case RideEventConfig.ROUTING_PAYMENT_INITIATED ->
                        handlePaymentInitiated(rideId);
                case RideEventConfig.ROUTING_PAYMENT_COMPLETED ->
                        handlePaymentCompleted(rideId);
                case RideEventConfig.ROUTING_PAYMENT_FAILED ->
                        handlePaymentFailed(rideId, extractString(payload, "reason"));
                case RideEventConfig.ROUTING_PAYMENT_REFUNDED ->
                        handlePaymentRefunded(rideId);
                default ->
                        log.warn("Unknown routing key '{}' on ride.saga-feedback — skipping", routingKey);
            }
        } finally {
            MDC.remove("correlationId");
            MDC.remove("routingKey");
            MDC.remove("rideId");
            MDC.remove("paymentId");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Per-event handlers — each is @Transactional
    // ──────────────────────────────────────────────────────────────────────────

    /** COMPLETED → PAYMENT_PENDING (idempotent if already PAYMENT_PENDING). */
    @Transactional
    public void handlePaymentInitiated(Long rideId) {
        Optional<Ride> opt = rideRepository.findById(rideId);
        if (opt.isEmpty()) {
            log.warn("payment.initiated: ride {} not found — acknowledging", rideId);
            return;
        }
        Ride ride = opt.get();
        if (ride.getStatus() == RideStatus.PAYMENT_PENDING) {
            log.info("payment.initiated: ride {} already PAYMENT_PENDING — no-op (idempotent)", rideId);
            return;
        }
        if (ride.getStatus() != RideStatus.COMPLETED) {
            log.warn("payment.initiated: ride {} in unexpected status {} (expected COMPLETED) — skipping",
                    rideId, ride.getStatus());
            return;
        }
        ride.setStatus(RideStatus.PAYMENT_PENDING);
        rideRepository.save(ride);
        log.info("payment.initiated: ride {} transitioned COMPLETED → PAYMENT_PENDING", rideId);
    }

    /** PAYMENT_PENDING → PAID (idempotent if already PAID). */
    @Transactional
    public void handlePaymentCompleted(Long rideId) {
        Optional<Ride> opt = rideRepository.findById(rideId);
        if (opt.isEmpty()) {
            log.warn("payment.completed: ride {} not found — acknowledging", rideId);
            return;
        }
        Ride ride = opt.get();
        if (ride.getStatus() == RideStatus.PAID) {
            log.info("payment.completed: ride {} already PAID — no-op (idempotent)", rideId);
            return;
        }
        if (ride.getStatus() != RideStatus.PAYMENT_PENDING) {
            log.warn("payment.completed: ride {} in unexpected status {} (expected PAYMENT_PENDING) — skipping",
                    rideId, ride.getStatus());
            return;
        }
        ride.setStatus(RideStatus.PAID);
        rideRepository.save(ride);
        log.info("payment.completed: ride {} transitioned PAYMENT_PENDING → PAID", rideId);
    }

    /**
     * PAYMENT_PENDING → PAYMENT_FAILED, then publish ride.cancelled with
     * reason="payment_failed" to trigger the compensation cascade.
     * The publish is gated on an actual transition so it never fires on
     * a duplicate payment.failed redelivery.
     */
    @Transactional
    public void handlePaymentFailed(Long rideId, String reason) {
        Optional<Ride> opt = rideRepository.findById(rideId);
        if (opt.isEmpty()) {
            log.warn("payment.failed: ride {} not found — acknowledging", rideId);
            return;
        }
        Ride ride = opt.get();
        if (ride.getStatus() == RideStatus.PAYMENT_FAILED) {
            log.info("payment.failed: ride {} already PAYMENT_FAILED — no-op (idempotent, "
                    + "compensation already fired)", rideId);
            return;
        }
        if (ride.getStatus() != RideStatus.PAYMENT_PENDING) {
            log.warn("payment.failed: ride {} in unexpected status {} (expected PAYMENT_PENDING) — skipping",
                    rideId, ride.getStatus());
            return;
        }
        ride.setStatus(RideStatus.PAYMENT_FAILED);
        rideRepository.save(ride);
        log.info("payment.failed: ride {} transitioned PAYMENT_PENDING → PAYMENT_FAILED reason={}",
                rideId, reason);

        // Compensation trigger — publish-after-commit (§2.11). The downstream
        // consumers (driver-service, payment-service) handle their own state.
        RideCancelledEvent event = new RideCancelledEvent(
                ride.getId(),
                ride.getUserId(),
                ride.getDriverId(),
                "payment_failed"
        );
        rideEventPublisher.publishRideCancelled(event);
        log.info("payment.failed: published ride.cancelled compensation for ride {}", rideId);
    }

    /**
     * PAID → REFUNDED (manual refund) or PAYMENT_FAILED → REFUNDED (post-compensation).
     * Idempotent if already REFUNDED.
     */
    @Transactional
    public void handlePaymentRefunded(Long rideId) {
        Optional<Ride> opt = rideRepository.findById(rideId);
        if (opt.isEmpty()) {
            log.warn("payment.refunded: ride {} not found — acknowledging", rideId);
            return;
        }
        Ride ride = opt.get();
        if (ride.getStatus() == RideStatus.REFUNDED) {
            log.info("payment.refunded: ride {} already REFUNDED — no-op (idempotent)", rideId);
            return;
        }
        if (ride.getStatus() != RideStatus.PAID && ride.getStatus() != RideStatus.PAYMENT_FAILED) {
            log.warn("payment.refunded: ride {} in unexpected status {} (expected PAID or PAYMENT_FAILED)"
                    + " — skipping", rideId, ride.getStatus());
            return;
        }
        RideStatus prev = ride.getStatus();
        ride.setStatus(RideStatus.REFUNDED);
        rideRepository.save(ride);
        log.info("payment.refunded: ride {} transitioned {} → REFUNDED", rideId, prev);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Payload helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Jackson deserializes JSON numbers as {@link Integer} when they fit and
     * {@link Long} otherwise — and string IDs as {@link String}. This helper
     * normalizes all three.
     */
    private static Long extractLong(Map<String, Object> payload, String key) {
        Object raw = payload.get(key);
        if (raw == null) return null;
        if (raw instanceof Number n) return n.longValue();
        if (raw instanceof String s) {
            try { return Long.valueOf(s); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private static String extractString(Map<String, Object> payload, String key) {
        Object raw = payload.get(key);
        return raw == null ? null : raw.toString();
    }
}