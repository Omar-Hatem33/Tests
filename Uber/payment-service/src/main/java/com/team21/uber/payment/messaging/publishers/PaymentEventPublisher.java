package com.team21.uber.payment.messaging.publishers;

import com.team21.uber.contracts.events.PaymentInitiatedEvent;
import com.team21.uber.contracts.events.PaymentCompletedEvent;
import com.team21.uber.contracts.events.PaymentFailedEvent;
import com.team21.uber.contracts.events.PaymentRefundedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(PaymentEventPublisher.class);

    public PaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPaymentInitiated(PaymentInitiatedEvent event) {
        rabbitTemplate.convertAndSend("payment.events", "payment.initiated", event);
        log.info("Published payment.initiated for rideId={}", event.rideId());
    }

    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        rabbitTemplate.convertAndSend("payment.events", "payment.completed", event);
        log.info("Published payment.completed for rideId={}", event.rideId());
    }

    public void publishPaymentFailed(PaymentFailedEvent event) {
        rabbitTemplate.convertAndSend("payment.events", "payment.failed", event);
        log.info("Published payment.failed for rideId={}", event.rideId());
    }

    public void publishPaymentRefunded(PaymentRefundedEvent event) {
        rabbitTemplate.convertAndSend("payment.events", "payment.refunded", event);
        log.info("Published payment.refunded for rideId={}", event.rideId());
    }
}