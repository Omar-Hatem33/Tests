package com.team21.uber.payment.messaging.consumers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team21.uber.contracts.events.RideCompletedEvent;
import com.team21.uber.contracts.events.RideCancelledEvent;
import com.team21.uber.contracts.events.PaymentInitiatedEvent;
import com.team21.uber.contracts.events.PaymentRefundedEvent;
import com.team21.uber.payment.messaging.publishers.PaymentEventPublisher;
import com.team21.uber.payment.model.Payment;
import com.team21.uber.payment.model.PaymentStatus;
import com.team21.uber.payment.dto.RefundRequestDTO;
import com.team21.uber.payment.repository.PaymentRepository;
import com.team21.uber.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Component
public class RideEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RideEventConsumer.class);

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public RideEventConsumer(PaymentRepository paymentRepository,
                             PaymentEventPublisher paymentEventPublisher,
                             PaymentService paymentService,
                             ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.paymentEventPublisher = paymentEventPublisher;
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "payment.saga-listener")
    @Transactional
    public void handleRideEvent(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        MDC.put("routingKey", routingKey);
        try {
            if ("ride.completed".equals(routingKey)) {
                RideCompletedEvent event = objectMapper.readValue(
                        message.getBody(), RideCompletedEvent.class);
                handleRideCompleted(event);
            } else if ("ride.cancelled".equals(routingKey)) {
                RideCancelledEvent event = objectMapper.readValue(
                        message.getBody(), RideCancelledEvent.class);
                handleRideCancelled(event);
            } else {
                log.warn("Received unknown routing key on payment.saga-listener: {}", routingKey);
            }
        } catch (Exception e) {
            log.error("Failed to process {}: {}", routingKey, e.getMessage());
            // Re-throw so Spring retries and eventually routes to DLQ
            throw new RuntimeException("Failed to process event with routing key: " + routingKey, e);
        } finally {
            MDC.remove("routingKey");
        }
    }

    private void handleRideCompleted(RideCompletedEvent event) {
        MDC.put("rideId", event.rideId().toString());
        try {
            log.info("Consuming ride.completed for rideId={}", event.rideId());

            // Idempotency — skip if payment already exists for this ride
            boolean exists = paymentRepository.existsByRideId(event.rideId());
            if (exists) {
                log.info("Payment already exists for rideId={} — skipping", event.rideId());
                return;
            }

            Payment payment = new Payment();
            payment.setRideId(event.rideId());
            payment.setUserId(event.userId());
            payment.setAmount(event.fare());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setCreatedAt(LocalDateTime.now());
            payment.setTransactionDetails(new HashMap<>());
            payment.setPaymentCoupons(new ArrayList<>());
            paymentRepository.save(payment);

            log.info("Payment {} saved with status=PENDING for rideId={}",
                    payment.getId(), event.rideId());

            paymentEventPublisher.publishPaymentInitiated(
                    new PaymentInitiatedEvent(payment.getId(), event.rideId(), event.fare())
            );

            log.info("Processed ride.completed for rideId={}", event.rideId());

        } finally {
            MDC.remove("rideId");
        }
    }

    private void handleRideCancelled(RideCancelledEvent event) {
        MDC.put("rideId", event.rideId().toString());
        try {
            log.info("Consuming ride.cancelled for rideId={}", event.rideId());

            Optional<Payment> paymentOpt = paymentRepository
                    .findByRideIdAndStatusIn(
                            event.rideId(),
                            List.of(PaymentStatus.PENDING, PaymentStatus.COMPLETED)
                    );

            if (paymentOpt.isEmpty()) {
                log.info("No payment found for rideId={} — skipping refund", event.rideId());
                return;
            }

            Payment payment = paymentOpt.get();

            // Idempotency — skip if already refunded
            if (payment.getStatus() == PaymentStatus.REFUNDED) {
                log.info("Payment {} already refunded — skipping", payment.getId());
                return;
            }

            // Reuse M2 S5-F12 Strategy refund logic
            RefundRequestDTO refundRequest = new RefundRequestDTO();
            refundRequest.setReason("ride_cancelled");
            refundRequest.setRefundSurge(true);

            var refundResponse = paymentService.refundSurgeAdjusted(
                    payment.getId(), refundRequest);

            log.info("Payment {} saved with status=REFUNDED for rideId={}",
                    payment.getId(), event.rideId());

            paymentEventPublisher.publishPaymentRefunded(
                    new PaymentRefundedEvent(
                            payment.getId(),
                            event.rideId(),
                            refundResponse.getRefundAmount()
                    )
            );

            log.info("Processed ride.cancelled for rideId={}", event.rideId());

        } finally {
            MDC.remove("rideId");
        }
    }
}