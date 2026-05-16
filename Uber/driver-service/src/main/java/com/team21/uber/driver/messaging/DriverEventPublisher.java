package com.team21.uber.driver.messaging;

import com.team21.uber.driver.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Publishes driver domain events to the driver.events TopicExchange.
 */
@Component
public class DriverEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DriverEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public DriverEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishStatusChanged(Long driverId, String oldStatus, String newStatus) {
        try {
            Map<String, Object> payload = Map.of(
                    "driverId", driverId,
                    "oldStatus", oldStatus != null ? oldStatus : "",
                    "newStatus", newStatus != null ? newStatus : ""
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.DRIVER_EVENTS_EXCHANGE,
                    RabbitMQConfig.DRIVER_STATUS_CHANGED_KEY,
                    payload);
            log.info("Published driver.status-changed for driverId={}", driverId);
        } catch (Exception e) {
            log.warn("Failed to publish driver.status-changed: {}", e.getMessage());
        }
    }

    public void publishDriverRated(Long driverId, Long rideId, Double rating, Long userId) {
        try {
            Map<String, Object> payload = Map.of(
                    "driverId", driverId,
                    "rideId", rideId,
                    "rating", rating,
                    "userId", userId != null ? userId : 0L
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.DRIVER_EVENTS_EXCHANGE,
                    RabbitMQConfig.DRIVER_RATED_KEY,
                    payload);
            log.info("Published driver.rated for driverId={} rideId={}", driverId, rideId);
        } catch (Exception e) {
            log.warn("Failed to publish driver.rated: {}", e.getMessage());
        }
    }

    public void publishDocumentVerified(Long driverId, Long documentId, Long verifiedBy) {
        try {
            Map<String, Object> payload = Map.of(
                    "driverId", driverId,
                    "documentId", documentId,
                    "verifiedBy", verifiedBy != null ? verifiedBy : 0L
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.DRIVER_EVENTS_EXCHANGE,
                    RabbitMQConfig.DRIVER_DOCUMENT_VERIFIED_KEY,
                    payload);
            log.info("Published driver.document.verified for driverId={}", driverId);
        } catch (Exception e) {
            log.warn("Failed to publish driver.document.verified: {}", e.getMessage());
        }
    }
}
