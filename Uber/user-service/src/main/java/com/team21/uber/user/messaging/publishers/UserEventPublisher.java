package com.team21.uber.user.messaging.publishers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team21.uber.contracts.events.UserDeactivatedEvent;
import com.team21.uber.contracts.events.UserRegisteredEvent;
import com.team21.uber.user.events.OutboxEvent;
import com.team21.uber.user.events.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private static final String EXCHANGE = "user.events";
    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public UserEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publishUserRegistered(Long userId, String email, String role) {
        save("user.registered", new UserRegisteredEvent(userId, email, role));
        log.info("Outbox stored user.registered userId={}", userId);
    }

    public void publishUserDeactivated(Long userId) {
        save("user.deactivated", new UserDeactivatedEvent(userId));
        log.info("Outbox stored user.deactivated userId={}", userId);
    }

    private void save(String routingKey, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            outboxRepository.save(new OutboxEvent(
                    EXCHANGE, routingKey, event.getClass().getName(), json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event " + routingKey, e);
        }
    }
}
