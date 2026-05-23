package com.team21.uber.user.messaging.consumers;

import com.team21.uber.contracts.events.RideCompletedEvent;
import com.team21.uber.contracts.events.RideCancelledEvent;
import com.team21.uber.user.model.User;
import com.team21.uber.user.repository.UserRepository;
import org.springframework.amqp.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RideEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RideEventConsumer.class);

    private final UserRepository userRepository;

    public RideEventConsumer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @RabbitListener(queues = "user.ride.saga-listener")
    public void handleRideCompleted(Message message, RideCompletedEvent event) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();

        if ("ride.completed".equals(routingKey)) {
            log.info("Received ride.completed for rideId={}, userId={}", event.rideId(), event.userId());
            User user = userRepository.findById(event.userId()).orElse(null);
            //missing logic

        } else if ("ride.cancelled".equals(routingKey)) {
            log.info("Received ride.cancelled for rideId={}, userId={}", event.rideId(), event.userId());
            User user = userRepository.findById(event.userId()).orElse(null);
            //missing logic
        }
    }
}