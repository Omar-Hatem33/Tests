package com.team21.uber.user.messaging.publishers;

import com.team21.uber.contracts.events.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.team21.uber.contracts.events.UserDeactivatedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public UserEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishUserRegistered(Long userId, String email, String role) {
        UserRegisteredEvent event = new UserRegisteredEvent(userId, email, role);
        rabbitTemplate.convertAndSend("user.events", "user.registered", event);
        log.info("Published user.registered for userId={}", userId);
    }

    public void publishUserDeactivated(Long userId) {
        UserDeactivatedEvent event = new UserDeactivatedEvent(userId);
        rabbitTemplate.convertAndSend("user.events", "user.deactivated", event);
        log.info("Published user.deactivated for userId={}", userId);
    }
}
