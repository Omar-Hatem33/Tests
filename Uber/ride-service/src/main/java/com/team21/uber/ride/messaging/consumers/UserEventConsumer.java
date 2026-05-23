package com.team21.uber.ride.messaging.consumers;

import com.team21.uber.ride.config.RideEventConfig;
import com.team21.uber.ride.event.RideEvent;
import com.team21.uber.ride.repository.RideEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * UserEventConsumer — listens on the {@code ride.user.audit-listener} queue for
 * user lifecycle events published by user-service on the {@code user.events} exchange.
 *
 * Placed in {@code messaging/consumers/} per §12 spec:
 *   messaging/consumers/ = any non-saga event consumers
 *
 * This consumer is AUDIT-ONLY:
 *   - user.registered  → write RideEvent(action=USER_REGISTERED)  to MongoDB ride_events
 *   - user.deactivated → write RideEvent(action=USER_DEACTIVATED) to MongoDB ride_events
 *   - NO state mutation in ride-postgres on either event
 *
 * Idempotency: MongoDB writes are naturally idempotent here since each message
 * produces a new document with a fresh _id — redelivery creates a duplicate audit
 * entry rather than corrupting state. This is acceptable for audit logs; the
 * duplicate is visible in the audit trail with the same timestamp context.
 *
 * Payload contracts (from contracts module):
 *   UserRegisteredEvent  → { userId: Long, email: String, role: String }
 *   UserDeactivatedEvent → { userId: Long }
 *
 * Messages are consumed as Map<String,Object> to avoid __TypeId__ header coupling
 * across service boundaries. Dispatch is by routing key header.
 */
@Component
public class UserEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventConsumer.class);

    private final RideEventRepository rideEventRepository;

    public UserEventConsumer(RideEventRepository rideEventRepository) {
        this.rideEventRepository = rideEventRepository;
    }

    @RabbitListener(queues = RideEventConfig.RIDE_USER_AUDIT_QUEUE)
    public void onUserEvent(
            @Payload Map<String, Object> payload,
            @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey,
            @Header(name = "X-Correlation-ID", required = false) String correlationId) {

        String mdcCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        MDC.put("correlationId", mdcCorrelationId);
        MDC.put("routingKey", routingKey == null ? "" : routingKey);

        try {
            log.info("Received user event routingKey={} payload={}", routingKey, payload);

            if (routingKey == null) {
                log.error("User event missing routing key — acknowledging without action");
                return;
            }

            switch (routingKey) {
                case RideEventConfig.ROUTING_USER_REGISTERED  -> handleUserRegistered(payload);
                case RideEventConfig.ROUTING_USER_DEACTIVATED -> handleUserDeactivated(payload);
                default -> log.warn("Unknown routing key '{}' on ride.user.audit-listener — skipping",
                        routingKey);
            }
        } finally {
            MDC.remove("correlationId");
            MDC.remove("routingKey");
            MDC.remove("userId");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Handlers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Writes an audit entry for a new user registration.
     * Captures userId, email, and role from the event payload.
     * rideId is null — this event is not associated with any ride.
     */
    private void handleUserRegistered(Map<String, Object> payload) {
        Long userId = extractLong(payload, "userId");
        if (userId != null) MDC.put("userId", String.valueOf(userId));

        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);
        details.put("email",  extractString(payload, "email"));
        details.put("role",   extractString(payload, "role"));

        RideEvent event = new RideEvent(
                null,                   // rideId — not applicable for user events
                "USER_REGISTERED",
                LocalDateTime.now(),
                details
        );
        rideEventRepository.save(event);
        log.info("Audit: USER_REGISTERED userId={} email={}", userId, details.get("email"));
    }

    /**
     * Writes an audit entry when a user account is deactivated.
     * rideId is null — this event is not associated with any specific ride.
     */
    private void handleUserDeactivated(Map<String, Object> payload) {
        Long userId = extractLong(payload, "userId");
        if (userId != null) MDC.put("userId", String.valueOf(userId));

        Map<String, Object> details = new HashMap<>();
        details.put("userId", userId);

        RideEvent event = new RideEvent(
                null,                   // rideId — not applicable for user events
                "USER_DEACTIVATED",
                LocalDateTime.now(),
                details
        );
        rideEventRepository.save(event);
        log.info("Audit: USER_DEACTIVATED userId={}", userId);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Payload helpers
    // ──────────────────────────────────────────────────────────────────────────

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