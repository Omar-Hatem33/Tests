package com.team21.uber.ride.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the ride-service.
 *
 * Producer side:
 *   - {@code ride.events} TopicExchange — ride.placed (S3-F2), ride.completed (S3-F4),
 *     ride.cancelled (S3-F7 and payment.failed compensation).
 *
 * Consumer side — payment saga (S3-payment-consumers):
 *   - {@code payment.events} TopicExchange reference
 *   - {@code ride.saga-feedback} queue with DLX/DLQ — bound to payment.* routing keys
 *
 * Consumer side — user audit (S3-user-consumers):
 *   - {@code user.events} TopicExchange reference
 *   - {@code ride.user.audit-listener} queue with DLX/DLQ — bound to user.registered
 *     and user.deactivated; audit-only, no ride-postgres state mutation
 */
@Configuration
public class RideEventConfig {

    // ── Exchange names ────────────────────────────────────────────────────────
    public static final String RIDE_EVENTS_EXCHANGE    = "ride.events";
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment.events";
    public static final String USER_EVENTS_EXCHANGE    = "user.events";

    // ── Queue names — payment saga ────────────────────────────────────────────
    public static final String RIDE_SAGA_FEEDBACK_QUEUE = "ride.saga-feedback";
    public static final String RIDE_SAGA_FEEDBACK_DLX   = "ride.saga-feedback.dlx";
    public static final String RIDE_SAGA_FEEDBACK_DLQ   = "ride.saga-feedback.dlq";

    // ── Queue names — user audit ──────────────────────────────────────────────
    public static final String RIDE_USER_AUDIT_QUEUE = "ride.user.audit-listener";
    public static final String RIDE_USER_AUDIT_DLX   = "ride.user.audit-listener.dlx";
    public static final String RIDE_USER_AUDIT_DLQ   = "ride.user.audit-listener.dlq";

    // ── Routing keys — ride producers ─────────────────────────────────────────
    public static final String ROUTING_RIDE_PLACED    = "ride.placed";
    public static final String ROUTING_RIDE_COMPLETED = "ride.completed";
    public static final String ROUTING_RIDE_CANCELLED = "ride.cancelled";

    // ── Routing keys — payment consumers ─────────────────────────────────────
    public static final String ROUTING_PAYMENT_INITIATED = "payment.initiated";
    public static final String ROUTING_PAYMENT_COMPLETED = "payment.completed";
    public static final String ROUTING_PAYMENT_FAILED    = "payment.failed";
    public static final String ROUTING_PAYMENT_REFUNDED  = "payment.refunded";

    // ── Routing keys — user consumers ────────────────────────────────────────
    public static final String ROUTING_USER_REGISTERED  = "user.registered";
    public static final String ROUTING_USER_DEACTIVATED = "user.deactivated";

    // ──────────────────────────────────────────────────────────────────────────
    // Exchanges
    // ──────────────────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange rideEventsExchange() {
        return ExchangeBuilder.topicExchange(RIDE_EVENTS_EXCHANGE).durable(true).build();
    }

    /** Reference to payment-service-owned exchange. Safe to re-declare; AMQP deduplicates. */
    @Bean
    public TopicExchange paymentEventsExchange() {
        return ExchangeBuilder.topicExchange(PAYMENT_EVENTS_EXCHANGE).durable(true).build();
    }

    /** Reference to user-service-owned exchange. Safe to re-declare; AMQP deduplicates. */
    @Bean
    public TopicExchange userEventsExchange() {
        return ExchangeBuilder.topicExchange(USER_EVENTS_EXCHANGE).durable(true).build();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Payment saga feedback — DLX, DLQ, main queue, 4 bindings
    // ──────────────────────────────────────────────────────────────────────────

    @Bean
    public DirectExchange rideSagaFeedbackDlx() {
        return ExchangeBuilder.directExchange(RIDE_SAGA_FEEDBACK_DLX).durable(true).build();
    }

    @Bean
    public Queue rideSagaFeedbackDlq() {
        return QueueBuilder.durable(RIDE_SAGA_FEEDBACK_DLQ).build();
    }

    @Bean
    public Binding rideSagaFeedbackDlqBinding() {
        return BindingBuilder.bind(rideSagaFeedbackDlq())
                .to(rideSagaFeedbackDlx()).with(RIDE_SAGA_FEEDBACK_DLQ);
    }

    @Bean
    public Queue rideSagaFeedbackQueue() {
        return QueueBuilder.durable(RIDE_SAGA_FEEDBACK_QUEUE)
                .withArgument("x-dead-letter-exchange", RIDE_SAGA_FEEDBACK_DLX)
                .withArgument("x-dead-letter-routing-key", RIDE_SAGA_FEEDBACK_DLQ)
                .build();
    }

    @Bean public Binding paymentInitiatedBinding() {
        return BindingBuilder.bind(rideSagaFeedbackQueue())
                .to(paymentEventsExchange()).with(ROUTING_PAYMENT_INITIATED);
    }
    @Bean public Binding paymentCompletedBinding() {
        return BindingBuilder.bind(rideSagaFeedbackQueue())
                .to(paymentEventsExchange()).with(ROUTING_PAYMENT_COMPLETED);
    }
    @Bean public Binding paymentFailedBinding() {
        return BindingBuilder.bind(rideSagaFeedbackQueue())
                .to(paymentEventsExchange()).with(ROUTING_PAYMENT_FAILED);
    }
    @Bean public Binding paymentRefundedBinding() {
        return BindingBuilder.bind(rideSagaFeedbackQueue())
                .to(paymentEventsExchange()).with(ROUTING_PAYMENT_REFUNDED);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // User audit listener — DLX, DLQ, main queue, 2 bindings
    // ──────────────────────────────────────────────────────────────────────────

    @Bean
    public DirectExchange rideUserAuditDlx() {
        return ExchangeBuilder.directExchange(RIDE_USER_AUDIT_DLX).durable(true).build();
    }

    @Bean
    public Queue rideUserAuditDlq() {
        return QueueBuilder.durable(RIDE_USER_AUDIT_DLQ).build();
    }

    @Bean
    public Binding rideUserAuditDlqBinding() {
        return BindingBuilder.bind(rideUserAuditDlq())
                .to(rideUserAuditDlx()).with(RIDE_USER_AUDIT_DLQ);
    }

    /**
     * Durable queue consuming user lifecycle events for MongoDB audit logging.
     * Audit-only — no write to ride-postgres on any message.
     * Poison messages are routed to the DLX/DLQ rather than blocking the queue.
     */
    @Bean
    public Queue rideUserAuditQueue() {
        return QueueBuilder.durable(RIDE_USER_AUDIT_QUEUE)
                .withArgument("x-dead-letter-exchange", RIDE_USER_AUDIT_DLX)
                .withArgument("x-dead-letter-routing-key", RIDE_USER_AUDIT_DLQ)
                .build();
    }

    @Bean
    public Binding userRegisteredBinding() {
        return BindingBuilder.bind(rideUserAuditQueue())
                .to(userEventsExchange()).with(ROUTING_USER_REGISTERED);
    }

    @Bean
    public Binding userDeactivatedBinding() {
        return BindingBuilder.bind(rideUserAuditQueue())
                .to(userEventsExchange()).with(ROUTING_USER_DEACTIVATED);
    }
}