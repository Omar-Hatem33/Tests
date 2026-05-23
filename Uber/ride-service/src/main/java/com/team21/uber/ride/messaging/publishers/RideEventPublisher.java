package com.team21.uber.ride.messaging.publishers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team21.uber.ride.config.RideEventConfig;
import com.team21.uber.ride.events.OutboxEvent;
import com.team21.uber.ride.events.OutboxRepository;
import com.team21.uber.contracts.events.RidePlacedEvent;
import com.team21.uber.contracts.events.RideCompletedEvent;
import com.team21.uber.contracts.events.RideCancelledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class RideEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RideEventPublisher.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RideEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publishRidePlaced(RidePlacedEvent event) {
        save("ride.placed", event,
                "rideId=" + event.rideId() + " userId=" + event.userId() + " driverId=" + event.driverId());
    }

    public void publishRideCompleted(RideCompletedEvent event) {
        save("ride.completed", event,
                "rideId=" + event.rideId() + " userId=" + event.userId()
                        + " driverId=" + event.driverId() + " fare=" + event.fare());
    }

    public void publishRideCancelled(RideCancelledEvent event) {
        save("ride.cancelled", event,
                "rideId=" + event.rideId() + " userId=" + event.userId()
                        + " driverId=" + event.driverId() + " reason=" + event.reason());
    }

    private void save(String routingKey, Object event, String summary) {
        try {
            MDC.put("routingKey", routingKey);
            String json = objectMapper.writeValueAsString(event);
            outboxRepository.save(new OutboxEvent(
                    RideEventConfig.RIDE_EVENTS_EXCHANGE,
                    routingKey,
                    event.getClass().getName(),
                    json));
            log.info("Outbox stored {} ({})", routingKey, summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event " + routingKey, e);
        } finally {
            MDC.remove("routingKey");
        }
    }
}
