package com.team21.uber.location.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team21.uber.contracts.events.LocationTrackedEvent;
import com.team21.uber.contracts.events.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LocationRabbitPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocationRabbitPublisher.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public LocationRabbitPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publishTracked(LocationTrackedEvent e) {
        try {
            String json = objectMapper.writeValueAsString(e);
            outboxRepository.save(new OutboxEvent(
                    Topology.LOCATION_EVENTS,
                    Topology.RK_LOCATION_TRACKED,
                    e.getClass().getName(),
                    json));
            log.info("Outbox stored location.tracked");
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize LocationTrackedEvent: {}", ex.getMessage());
        }
    }
}
