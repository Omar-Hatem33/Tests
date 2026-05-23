package com.team21.uber.payment.messaging.publishers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team21.uber.contracts.events.PaymentInitiatedEvent;
import com.team21.uber.contracts.events.PaymentCompletedEvent;
import com.team21.uber.contracts.events.PaymentFailedEvent;
import com.team21.uber.contracts.events.PaymentRefundedEvent;
import com.team21.uber.payment.events.OutboxEvent;
import com.team21.uber.payment.events.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private static final String EXCHANGE = "payment.events";
    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publishPaymentInitiated(PaymentInitiatedEvent event) {
        save("payment.initiated", event);
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        save("payment.completed", event);
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        save("payment.failed", event);
    }

    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        save("payment.refunded", event);
    }

    private void save(String routingKey, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            outboxRepository.save(new OutboxEvent(
                    EXCHANGE, routingKey, event.getClass().getName(), json));
            log.info("Outbox stored {} payload={}", routingKey, json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event for routingKey=" + routingKey, e);
        }
    }
}
