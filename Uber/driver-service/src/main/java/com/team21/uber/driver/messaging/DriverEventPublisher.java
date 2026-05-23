package com.team21.uber.driver.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team21.uber.driver.config.RabbitMQConfig;
import com.team21.uber.driver.events.OutboxEvent;
import com.team21.uber.driver.events.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DriverEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DriverEventPublisher.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DriverEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publishStatusChanged(Long driverId, String oldStatus, String newStatus) {
        save(RabbitMQConfig.DRIVER_STATUS_CHANGED_KEY, Map.of(
                "driverId", driverId,
                "oldStatus", oldStatus != null ? oldStatus : "",
                "newStatus", newStatus != null ? newStatus : ""));
        log.info("Outbox stored driver.status-changed driverId={}", driverId);
    }

    public void publishDriverRated(Long driverId, Long rideId, Double rating, Long userId) {
        save(RabbitMQConfig.DRIVER_RATED_KEY, Map.of(
                "driverId", driverId,
                "rideId", rideId,
                "rating", rating,
                "userId", userId != null ? userId : 0L));
        log.info("Outbox stored driver.rated driverId={} rideId={}", driverId, rideId);
    }

    public void publishDocumentVerified(Long driverId, Long documentId, Long verifiedBy) {
        save(RabbitMQConfig.DRIVER_DOCUMENT_VERIFIED_KEY, Map.of(
                "driverId", driverId,
                "documentId", documentId,
                "verifiedBy", verifiedBy != null ? verifiedBy : 0L));
        log.info("Outbox stored driver.document.verified driverId={}", driverId);
    }

    private void save(String routingKey, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(new OutboxEvent(
                    RabbitMQConfig.DRIVER_EVENTS_EXCHANGE,
                    routingKey,
                    payload.getClass().getName(),
                    json));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize driver event {}: {}", routingKey, e.getMessage());
        }
    }
}
